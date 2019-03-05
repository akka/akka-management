/*
 * Copyright (C) 2017-2018 Lightbend Inc. <http://www.lightbend.com>
 */

package akka.management.cluster.bootstrap.internal

import java.time.LocalDateTime
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.TimeoutException

import scala.concurrent.Future
import scala.concurrent.duration._
import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.DeadLetterSuppression
import akka.actor.Props
import akka.actor.Status
import akka.actor.Timers
import akka.annotation.InternalApi
import akka.cluster.Cluster
import akka.discovery.ServiceDiscovery.ResolvedTarget
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.management.cluster.bootstrap.ClusterBootstrapSettings
import akka.management.cluster.bootstrap.contactpoint.ClusterBootstrapRequests
import akka.management.cluster.bootstrap.contactpoint.HttpBootstrapJsonProtocol
import akka.management.cluster.bootstrap.contactpoint.HttpBootstrapJsonProtocol.SeedNodes
import akka.pattern.after
import akka.pattern.pipe
import akka.stream.ActorMaterializer

@InternalApi
private[bootstrap] object HttpContactPointBootstrap {

  def props(settings: ClusterBootstrapSettings, contactPoint: ResolvedTarget, baseUri: Uri): Props =
    Props(new HttpContactPointBootstrap(settings, contactPoint, baseUri))

  private case object ProbeTick extends DeadLetterSuppression
  private val ProbingTimerKey = "probing-key"
}

/**
 * Intended to be spawned as child actor by a higher-level Bootstrap coordinator that manages obtaining of the URIs.
 *
 * This additional step may at-first seem superficial -- after all, we already have some addresses of the nodes
 * that we'll want to join -- however it is not optional. By communicating with the actual nodes before joining their
 * cluster we're able to inquire about their status, double-check if perhaps they are part of an existing cluster already
 * that we should join, or even coordinate rolling upgrades or more advanced patterns.
 */
@InternalApi
private[bootstrap] class HttpContactPointBootstrap(
    settings: ClusterBootstrapSettings,
    contactPoint: ResolvedTarget,
    baseUri: Uri
) extends Actor
    with ActorLogging
    with Timers
    with HttpBootstrapJsonProtocol {

  import HttpContactPointBootstrap.ProbeTick
  import HttpContactPointBootstrap.ProbingTimerKey

  private val cluster = Cluster(context.system)

  if (baseUri.authority.host.address() == cluster.selfAddress.host.getOrElse("---") &&
      baseUri.authority.port == cluster.selfAddress.port.getOrElse(-1)) {
    throw new IllegalArgumentException(
        "Requested base Uri to be probed matches local remoting address, bailing out! " +
        s"Uri: $baseUri, this node's remoting address: ${cluster.selfAddress}")
  }

  private implicit val mat = ActorMaterializer()(context.system)
  private val http = Http()(context.system)
  import context.dispatcher

  private val probeInterval = settings.contactPoint.probeInterval
  private val probeRequest = ClusterBootstrapRequests.bootstrapSeedNodes(baseUri)
  private val replyTimeout = Future.failed(new TimeoutException(s"Probing timeout of [$baseUri]"))

  /**
   * If probing keeps failing until the deadline triggers, we notify the parent,
   * such that it rediscover again.
   */
  private var probingKeepFailingDeadline: Deadline = settings.contactPoint.probingFailureTimeout.fromNow

  private def resetProbingKeepFailingWithinDeadline(): Unit =
    probingKeepFailingDeadline = settings.contactPoint.probingFailureTimeout.fromNow

  override def preStart(): Unit =
    self ! ProbeTick

  override def receive = {
    case ProbeTick ⇒
      val req = ClusterBootstrapRequests.bootstrapSeedNodes(baseUri)
      log.debug("Probing [{}] for seed nodes...", req.uri)

      val reply = http.singleRequest(probeRequest).flatMap(handleResponse)

      val afterTimeout = after(settings.contactPoint.probingFailureTimeout, context.system.scheduler)(replyTimeout)
      Future.firstCompletedOf(List(reply, afterTimeout)).pipeTo(self)

    case Status.Failure(cause) =>
      log.warning("Probing [{}] failed due to: {}", probeRequest.uri, cause.getMessage)
      if (probingKeepFailingDeadline.isOverdue()) {
        log.error("Overdue of probing-failure-timeout, stop probing, signaling that it's failed")
        context.parent ! BootstrapCoordinator.Protocol.ProbingFailed(contactPoint, cause)
        context.stop(self)
      } else {
        // keep probing, hoping the request will eventually succeed
        scheduleNextContactPointProbing()
      }

    case response: SeedNodes ⇒
      notifyParentAboutSeedNodes(response)
      resetProbingKeepFailingWithinDeadline()
      // we keep probing and looking if maybe a cluster does form after all
      // (technically could be long polling or web-sockets, but that would need reconnect logic, so this is simpler)
      scheduleNextContactPointProbing()
  }

  private def handleResponse(response: HttpResponse): Future[SeedNodes] = {
    val strictEntity = response.entity.toStrict(1.second)

    if (response.status == StatusCodes.OK)
      strictEntity.flatMap(res ⇒ Unmarshal(res).to[SeedNodes])
    else
      strictEntity.flatMap { entity =>
        val body = entity.data.utf8String
        Future.failed(
            new IllegalStateException(s"Expected response '200 OK' but found ${response.status}. Body: '$body'"))
      }
  }

  private def notifyParentAboutSeedNodes(members: SeedNodes): Unit = {
    val seedAddresses = members.seedNodes.map(_.node)
    context.parent ! BootstrapCoordinator.Protocol.ObtainedHttpSeedNodesObservation(timeNow(), contactPoint,
      members.selfNode, seedAddresses)
  }

  private def scheduleNextContactPointProbing(): Unit =
    timers.startSingleTimer(ProbingTimerKey, ProbeTick, effectiveProbeInterval())

  /** Duration with configured jitter applied */
  private def effectiveProbeInterval(): FiniteDuration =
    probeInterval + jitter(probeInterval)

  def jitter(d: FiniteDuration): FiniteDuration =
    (d.toMillis * settings.contactPoint.probeIntervalJitter * ThreadLocalRandom.current().nextDouble()).millis

  protected def timeNow(): LocalDateTime =
    LocalDateTime.now()

}
