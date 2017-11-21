/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.cluster.bootstrap.dns

import akka.actor.{ Actor, ActorLogging, ActorRef, Props, Timers }
import akka.annotation.InternalApi
import akka.cluster.Cluster
import akka.cluster.bootstrap.ClusterBootstrapSettings
import akka.cluster.bootstrap.http.HttpBootstrapJsonProtocol.SeedNodes
import akka.cluster.bootstrap.http.{ ClusterBootstrapRequests, HttpBootstrapJsonProtocol }
import akka.compat.Future
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.{ HttpMethods, HttpRequest, Uri }
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.pattern.pipe
import akka.stream.ActorMaterializer

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
class HttpContactPointBootstrap(
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
  private implicit val mat = ActorMaterializer()
  private val http = Http()(context.system)
  import context.dispatcher

  private val ProbingTimerKey = "probing-key"

  private val probeInterval = settings.httpProbeInterval
  private val probeJitter = settings.httpProbeIntervalJitter

  /**
   * If we don't observe any seed-nodes until the deadline triggers, we notify the parent about it,
   * such that it may make the decision to join this node to itself or not (initiating a new cluster).
   */
  private val existingClusterNotObservedWithinDeadline: Deadline = settings.stableMargin.fromNow

  override def receive = {
    case Internal.ProbeNow() ⇒
      val req = ClusterBootstrapRequests.bootstrapSeedNodes(baseUri, cluster.selfAddress)
      log.info("Probing {} for seed nodes...", req.uri)

      http.singleRequest(req).flatMap(res ⇒ Unmarshal(res).to[SeedNodes]).pipeTo(self)

    case response @ SeedNodes(node, members, oldest) ⇒
      if (members.isEmpty) {
        if (clusterNotObservedWithinDeadline) {
          permitParentToFormClusterIfPossible() // FIXME only signal this once?

          // if we are not the lowest address, we won't join ourselves,
          // and then we'll end up observing someone else forming the cluster, so we continue probing
          scheduleNextContactPointProbing()
        } else {
          // we keep probing and looking if maybe a cluster does form after all
          //
          // (technically could be long polling or websockets, but that would need reconnect logic, so this is simpler)
          scheduleNextContactPointProbing()
        }
      } else {
        performParentJoining(response)
        context.stop(self)
      }
  }

  private def clusterNotObservedWithinDeadline: Boolean =
    existingClusterNotObservedWithinDeadline.isOverdue()

  private def permitParentToFormClusterIfPossible(): Unit = {
    log.info("No seed-nodes obtained from {} within stable margin [{}], may want to initiate the cluster myself...",
      baseUri, settings.stableMargin)

    context.parent ! HeadlessServiceDnsBootstrap.Protocol.NoSeedNodesObtainedWithinDeadline()
  }

  private def performParentJoining(members: SeedNodes): Unit = {
    log.info("Found existing cluster, {} returned seed-nodes: {}", members.selfNode, members.seedNodes)

    val seedAddresses = members.seedNodes.map(_.node)
    context.parent ! HeadlessServiceDnsBootstrap.Protocol.ObtainedHttpSeedNodesObservation(members.selfNode,
      seedAddresses)
  }

  private def scheduleNextContactPointProbing(): Unit =
    timers.startSingleTimer(ProbingTimerKey, Internal.ProbeNow(), effectiveProbeInterval())

  /** Duration with configured jitter applied */
  private def effectiveProbeInterval(): FiniteDuration =
    probeInterval + (probeInterval.toMillis * probeJitter).toInt.millis

  private def timeNow(): Long = System.currentTimeMillis()

}
