/*
 * Copyright (C) 2017 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.management.cluster.bootstrap

import java.time.Duration
import java.time.LocalDateTime

import scala.collection.JavaConverters._
import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Try

import akka.actor.ActorSystem
import akka.actor.Address
import akka.discovery.SimpleServiceDiscovery.ResolvedTarget
import akka.event.Logging

/**
 * The decisions of joining existing seed-nodes or join self to form new
 * cluster is performed by the `JoinDecider` and the implementation is
 * defined in configuration so support different strategies.
 */
trait JoinDecider {

  /**
   * Decide if and how to join based on the gathered [[SeedNodesInformation]].
   * The returned `JoinDecision` can be [[KeepProbing]], [[JoinSelf]] or [[JoinOtherSeedNodes]].
   */
  def decide(info: SeedNodesInformation): Future[JoinDecision]

}

final class SeedNodesInformation(val currentTime: LocalDateTime,
                                 val contactPointsChangedAt: LocalDateTime,
                                 val contactPoints: Set[ResolvedTarget],
                                 val seedNodesObservations: Set[SeedNodesObservation]) {

  def hasSeedNodes: Boolean =
    seedNodesObservations.nonEmpty && seedNodesObservations.exists(_.seedNodes.nonEmpty)

  def allSeedNodes: Set[Address] =
    seedNodesObservations.flatMap(_.seedNodes)

  def getAllSeedNodes: java.util.Set[Address] =
    allSeedNodes.asJava

  /** Java API */
  def getContactPoints: java.util.Set[ResolvedTarget] =
    contactPoints.asJava

  /** Java API */
  def getSeedNodesObservations: java.util.Set[SeedNodesObservation] =
    seedNodesObservations.asJava

}

final class SeedNodesObservation(val observedAt: LocalDateTime,
                                 val contactPoint: ResolvedTarget,
                                 val sourceAddress: Address,
                                 val seedNodes: Set[Address]) {

  /** Java API */
  def getSeedNodes: java.util.Set[Address] =
    seedNodes.asJava
}

sealed trait JoinDecision

/**
 * Not ready to join yet, continue discovering contact points
 * and retrieve seed nodes.
 */
case object KeepProbing extends JoinDecision {

  /**
   * Java API: get the singleton instance
   */
  def getInstance: JoinDecision = this

  val asCompletedFuture: Future[JoinDecision] = Future.successful(KeepProbing)
}

/**
 * There is no existing cluster running and this node
 * decided to form a new cluster by joining itself.
 * Other nodes should discover this and join the same.
 */
case object JoinSelf extends JoinDecision {

  /**
   * Java API: get the singleton instance
   */
  def getInstance: JoinDecision = this

  val asCompletedFuture: Future[JoinDecision] = Future.successful(JoinSelf)
}

/**
 * Join existing cluster.
 *
 * The self `Address` will be removed from the returned `seedNodes` to
 * be sure that it's never joining itself via this decision.
 */
final case class JoinOtherSeedNodes(seedNodes: Set[Address]) extends JoinDecision {

  /** Java API */
  def this(seedNodes: java.util.Set[Address]) = this(seedNodes.asScala.toSet)

  def asCompletedFuture: Future[JoinDecision] = Future.successful(this)
}

/**
 * The decision of joining "self" is made by deterministically sorting the discovered service IPs
 * and picking the *lowest* address. Only the node with lowest address joins itself.
 *
 * If any of the contact-points returns a list of seed nodes it joins them immediately.
 *
 * Joining "self" is only done when enough number of contact points have been discovered (`required-contact-point-nr`)
 * and there have been no changes to the discovered contact points during the `stable-margin`.
 *
 * There must also be seed node observations from all discovered contact points before joining "self".
 */
class LowestAddressJoinDecider(system: ActorSystem, settings: ClusterBootstrapSettings) extends JoinDecider {
  private val log = Logging(system, getClass)

  override def decide(info: SeedNodesInformation): Future[JoinDecision] =
    if (info.hasSeedNodes) {
      JoinOtherSeedNodes(info.allSeedNodes.take(5)).asCompletedFuture
    } else if (info.contactPoints.size < settings.contactPointDiscovery.requiredContactPointsNr) {
      log.info("Discovered [{}] contact points, which is less than the required [{}], retrying",
        info.contactPoints.size, settings.contactPointDiscovery.requiredContactPointsNr)
      KeepProbing.asCompletedFuture
    } else if (!isPastStableMargin(info)) {
      log.debug(
          "Contact points observations have changed more recently than the stable-margin [{}], changed at [{}], " +
          "not joining myself. This process will be retried.", settings.contactPointDiscovery.stableMargin,
          info.contactPointsChangedAt)
      KeepProbing.asCompletedFuture
    } else {
      // no seed nodes
      val contactPointsWithoutSeedNodesObservations =
        info.contactPoints -- info.seedNodesObservations.map(_.contactPoint)
      if (contactPointsWithoutSeedNodesObservations.isEmpty) {
        // got info from all contact points as expected
        if (isAllowedToJoinSelf(info))
          JoinSelf.asCompletedFuture
        else {
          if (log.isInfoEnabled)
            log.info(
                "Exceeded stable margins without locating seed-nodes, however this node is NOT the lowest address " +
                "out of the discovered IPs in this deployment, thus NOT joining self. Expecting node [{}] " +
                "(out of [{}]) to perform the self-join and initiate the cluster.",
                lowestAddressContactPoint(info).getOrElse(""), info.contactPoints.mkString(", "))

          // the probing will continue until the lowest addressed node decides to join itself.
          // note, that due to DNS changes this may still become this node! We'll then await until the dns stableMargin
          // is exceeded and would decide to try joining self again (same code-path), that time successfully though.
          KeepProbing.asCompletedFuture
        }
      } else {
        // missing info from some contact points (e.g. because of probe failing)
        if (log.isInfoEnabled)
          log.info(
              "Exceeded stable margins but missing seed node information from some contact points [{}] (out of [{}])",
              contactPointsWithoutSeedNodesObservations.mkString(", "), info.contactPoints.mkString(", "))

        KeepProbing.asCompletedFuture
      }

    }

  // FIXME Do we really need settings.contactPoint.noSeedsStableMargin? Isn't the stable-margin enough?
  // It has been probing all known contact points for that duration

  private def isPastStableMargin(info: SeedNodesInformation): Boolean = {
    val contactPointsChanged = Duration.between(info.contactPointsChangedAt, info.currentTime)
    contactPointsChanged.toMillis >= settings.contactPointDiscovery.stableMargin.toMillis
  }

  private def isAllowedToJoinSelf(info: SeedNodesInformation): Boolean = {
    val bootstrap = ClusterBootstrap(system)

    // we KNOW this await is safe, since we set the value before we bind the HTTP things even
    val selfContactPoint =
      Try(Await.result(bootstrap.selfContactPoint, 10.second)).getOrElse(throw new IllegalStateException(
            "Bootstrap.selfContactPoint was NOT set! This is required for the bootstrap to work! " +
            "If binding bootstrap routes manually and not via akka-management"))

    // we check if a contact point is "us", by comparing host and port that we've bound to
    def lowestContactPointIsSelfManagement(lowest: ResolvedTarget): Boolean =
      lowest.host == selfContactPoint.authority.host.toString() &&
      lowest.port.getOrElse(selfContactPoint.authority.port) == selfContactPoint.authority.port

    lowestAddressContactPoint(info) match {
      case Some(lowest) => lowestContactPointIsSelfManagement(lowest)
      case None => false
    }
  }

  /**
   * Contact point with the "lowest" contact point address,
   * it is expected to join itself if no other cluster is found in the deployment.
   */
  private def lowestAddressContactPoint(info: SeedNodesInformation): Option[ResolvedTarget] =
    info.contactPoints.toList.sorted.headOption
}
