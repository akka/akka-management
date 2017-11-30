/*
 * Copyright (C) 2017 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.cluster.bootstrap.dns

import java.util.concurrent.ThreadLocalRandom

import akka.actor.{ Actor, ActorLogging, ActorRef, Address, Props, Timers }
import akka.annotation.InternalApi
import akka.cluster.Cluster
import akka.cluster.bootstrap.ClusterBootstrapSettings
import akka.cluster.bootstrap.contactpoint.HttpBootstrapJsonProtocol.SeedNodes
import akka.cluster.bootstrap.contactpoint.{ ClusterBootstrapRequests, HttpBootstrapJsonProtocol }
import akka.compat.Future
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.{ HttpMethods, HttpRequest, Uri }
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.pattern.pipe
import akka.stream.ActorMaterializer
import akka.util.PrettyDuration
import akka.actor.Status

import scala.concurrent.duration._

@InternalApi
private[dns] object HttpContactPointBootstrap {

  def props(settings: ClusterBootstrapSettings, notifyOnJoinReady: ActorRef, probeAddress: Uri): Props =
    Props(new HttpContactPointBootstrap(settings, notifyOnJoinReady, probeAddress))

  object Protocol {
    object Internal {
      final case class ProbeNow()
      final case class ContactPointProbeResponse()
    }
  }
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
private[dns] class HttpContactPointBootstrap(
    settings: ClusterBootstrapSettings,
    notifyActor: ActorRef,
    baseUri: Uri
) extends Actor
    with ActorLogging
    with Timers
    with HttpBootstrapJsonProtocol {

  import HttpContactPointBootstrap._
  import HttpContactPointBootstrap.Protocol._

  private val cluster = Cluster(context.system)

  if (baseUri.authority.host.address() == cluster.selfAddress.host.getOrElse("---") &&
      baseUri.authority.port == cluster.selfAddress.port.getOrElse(-1)) {
    throw new IllegalArgumentException(
        "Requested base Uri to be probed matches local remoting address, bailing out! " +
        s"Uri: $baseUri, this node's remoting address: ${cluster.selfAddress}")
  }

  private implicit val mat = ActorMaterializer()
  private val http = Http()(context.system)
  import context.dispatcher

  private val ProbingTimerKey = "probing-key"

  private val probeInterval = settings.contactPoint.probeInterval

  private val probeRequest = ClusterBootstrapRequests.bootstrapSeedNodes(baseUri)

  /**
   * If we don't observe any seed-nodes until the deadline triggers, we notify the parent about it,
   * such that it may make the decision to join this node to itself or not (initiating a new cluster).
   */
  private val existingClusterNotObservedWithinDeadline: Deadline = settings.contactPoint.noSeedsStableMargin.fromNow

  override def preStart(): Unit =
    self ! Internal.ProbeNow()

  override def receive = {
    case Internal.ProbeNow() ⇒
      log.debug("Probing {} for seed nodes...", probeRequest.uri)

      http
        .singleRequest(probeRequest)
        .flatMap(_.entity.toStrict(settings.contactPoint.probeTimeout))
        .flatMap(res ⇒ Unmarshal(res).to[SeedNodes])
        .pipeTo(self)

    case Status.Failure(cause) =>
      log.error("Probing {} failed due to {}", probeRequest.uri, cause.getMessage)
      // keep probing, hoping the request will eventually succeed
      scheduleNextContactPointProbing()

    case response @ SeedNodes(node, members) ⇒
      if (members.isEmpty) {
        if (clusterNotObservedWithinDeadline) {
          permitParentToFormClusterIfPossible()

          // if we are not the lowest address, we won't join ourselves,
          // and then we'll end up observing someone else forming the cluster, so we continue probing
          scheduleNextContactPointProbing()
        } else {
          // we keep probing and looking if maybe a cluster does form after all
          //
          // (technically could be long polling or web-sockets, but that would need reconnect logic, so this is simpler)
          scheduleNextContactPointProbing()
        }
      } else {
        notifyParentNoSeedNodesWereFoundWithinDeadline(response)
        // we notified the parent that it may join itself if it is the designated node,
        // since we did not observe any existing cluster. However, in case this node
        // can't join itself (it's not the lowest address), some other node will --
        // so we continue probing.
        //
        // Summing up, one of the following will happen:
        // A) this node is allowed to join itself, and does so, and stops this probing actor -- our job is done.
        // B) some other node triggers the same process and joins itself
        //    - in which case we'll notice seed-nodes in our probing sooner or later!
        scheduleNextContactPointProbing()
      }
  }

  private def clusterNotObservedWithinDeadline: Boolean =
    existingClusterNotObservedWithinDeadline.isOverdue()

  private def permitParentToFormClusterIfPossible(): Unit = {
    log.debug("No seed-nodes obtained from {} within stable margin [{}], may want to initiate the cluster myself...",
      baseUri, settings.contactPoint.noSeedsStableMargin)

    context.parent ! HeadlessServiceDnsBootstrap.Protocol.NoSeedNodesObtainedWithinDeadline(baseUri)
  }

  private def notifyParentNoSeedNodesWereFoundWithinDeadline(members: SeedNodes): Unit = {
    log.info("Found existing cluster, {} returned seed-nodes: {}", members.selfNode, members.seedNodes)

    val seedAddresses = members.seedNodes.map(_.node)
    context.parent ! HeadlessServiceDnsBootstrap.Protocol.ObtainedHttpSeedNodesObservation(members.selfNode,
      seedAddresses)
  }

  private def scheduleNextContactPointProbing(): Unit =
    timers.startSingleTimer(ProbingTimerKey, Internal.ProbeNow(), effectiveProbeInterval())

  /** Duration with configured jitter applied */
  private def effectiveProbeInterval(): FiniteDuration =
    probeInterval + jitter(probeInterval)

  def jitter(d: FiniteDuration): FiniteDuration =
    (d.toMillis * settings.contactPoint.probeIntervalJitter * ThreadLocalRandom.current().nextDouble()).millis

}
