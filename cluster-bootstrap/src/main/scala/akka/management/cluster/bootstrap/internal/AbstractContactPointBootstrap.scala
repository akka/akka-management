/*
 * Copyright (C) 2017-2018 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.management.cluster.bootstrap.internal

import java.time.LocalDateTime
import java.util.concurrent.ThreadLocalRandom

import akka.actor.{Actor, ActorLogging, DeadLetterSuppression, Status, Timers}
import akka.annotation.InternalApi
import akka.discovery.ServiceDiscovery.ResolvedTarget
import akka.management.cluster.bootstrap.ClusterBootstrapSettings
import akka.util.Timeout
import akka.pattern.pipe

import scala.concurrent.Future
import scala.concurrent.duration._

@InternalApi
private[bootstrap] object AbstractContactPointBootstrap {

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
private[bootstrap] abstract class AbstractContactPointBootstrap(
  settings: ClusterBootstrapSettings,
  contactPoint: ResolvedTarget
) extends Actor
  with ActorLogging
  with Timers {

  import AbstractContactPointBootstrap.ProbeTick
  import AbstractContactPointBootstrap.ProbingTimerKey
  import akka.management.cluster.bootstrap.contactpoint.HttpBootstrapJsonProtocol._
  import context.dispatcher

  private val probeInterval = settings.contactPoint.probeInterval
  private implicit val probingFailureTimeout: Timeout = Timeout(settings.contactPoint.probingFailureTimeout)

  /**
    * If probing keeps failing until the deadline triggers, we notify the parent,
    * such that it rediscover again.
    */
  private var probingKeepFailingDeadline: Deadline = settings.contactPoint.probingFailureTimeout.fromNow

  private def resetProbingKeepFailingWithinDeadline(): Unit =
    probingKeepFailingDeadline = settings.contactPoint.probingFailureTimeout.fromNow

  override final def preStart(): Unit =
    self ! ProbeTick

  override final def receive: Receive = {
    case ProbeTick ⇒
      log.debug("Probing [{}] for seed nodes...", uri)
      probe() pipeTo self

    case Status.Failure(cause) =>
      log.warning("Probing [{}] failed due to: {}", uri, cause.getMessage)
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

  /**
    * Probe the contact point.
    *
    * @param probingFailureTimeout A timeout, if not replied within this timeout, the returned Future should fail.
    * @return A future of the seed nodes.
    */
  protected def probe()(implicit probingFailureTimeout: Timeout): Future[SeedNodes]

  /**
    * Render the URI of the contact point as a string.
    *
    * This is used for logging purposes.
    */
  protected def uri: String

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
