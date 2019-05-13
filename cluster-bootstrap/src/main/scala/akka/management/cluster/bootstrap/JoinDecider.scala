/*
 * Copyright (C) 2017-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.management.cluster.bootstrap

import java.time.LocalDateTime

import scala.collection.JavaConverters._
import scala.concurrent.Future
import akka.actor.Address
import akka.discovery.ServiceDiscovery.ResolvedTarget

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

/**
 * Full information about discovered contact points and found seed nodes.
 *
 * `contactPoints` contains all nodes that were returned from the discovery (e.g. DNS lookup).
 *
 * `seedNodesObservations` contains the replies from those contact points when probing them
 * with the HTTP call. It only contains entries for the contact points that actually replied,
 * i.e. were reachable and running. Each such `SeedNodesObservation` entry has the `seedNodes`
 * (Akka Cluster addresses) that were returned from that contact point. That `Set` will be
 * empty if the node replied but is not part of an existing cluster yet, i.e. it hasn't joined.
 *
 * There are also some timestamps that can be interesting. Note that `currentTime` is passed in
 * to facilitate calculation of durations.
 *
 * `contactPointsChangedAt` is when the discovered contact points were last changed (e.g. via DNS lookup),
 * e.g. 5 seconds ago means that subsequent lookup attempts (1 per second) after that were successful and
 * returned the same set.
 *
 * `SeedNodesObservation.observedAt` was when that reply was received from that contact point.
 * The entry is removed if no reply was received within the `probing-failure-timeout` meaning that it
 * is unreachable or not running.
 */
final class SeedNodesInformation(val currentTime: LocalDateTime,
                                 val contactPointsChangedAt: LocalDateTime,
                                 val contactPoints: Set[ResolvedTarget],
                                 val seedNodesObservations: Set[SeedNodesObservation]) {

  def hasSeedNodes: Boolean =
    seedNodesObservations.nonEmpty && seedNodesObservations.exists(_.seedNodes.nonEmpty)

  def allSeedNodes: Set[Address] =
    seedNodesObservations.flatMap(_.seedNodes)

  /** Java API */
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

sealed trait JoinDecision {
  val asCompletedFuture: Future[JoinDecision] = Future.successful(this)
}

/**
 * Not ready to join yet, continue discovering contact points
 * and retrieve seed nodes.
 */
case object KeepProbing extends JoinDecision {

  /**
   * Java API: get the singleton instance
   */
  def getInstance: JoinDecision = this
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
}
