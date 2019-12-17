/*
 * Copyright (C) 2017-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.management.cluster.bootstrap

import akka.actor.Address
import akka.annotation.ApiMayChange
import akka.annotation.InternalApi
import akka.event.LogMarker

/**
 * This is public with the purpose to document the used markers and properties of log events.
 * No guarantee that it will remain binary compatible, but the marker names and properties
 * are considered public API and will not be changed without notice.
 */
@ApiMayChange
object BootstrapLogMarker {

  /**
   * INTERNAL API
   */
  @InternalApi private[akka] object Properties {
    val ContactPoints = "akkaContactPoints"
    val SeedNodes = "akkaSeedNodes"
  }

  /**
   * Marker "akkaBootstrapInit" of log event when Akka Bootstrap is initialized.
   */
  val init: LogMarker =
    LogMarker("akkaBootstrapInit")

  /**
   * Marker "akkaBootstrapResolved" of log event when contact points have been resolved.
   * @param contactPoints The hostname and port of the resolved and selected contact points. Included as property "akkaContactPoints".
   */
  def resolved(contactPoints: Iterable[String]): LogMarker =
    LogMarker("akkaBootstrapResolved", Map(Properties.ContactPoints -> contactPoints.mkString(", ")))

  /**
   * Marker "akkaBootstrapResolveFailed" of log event when resolve of contact points failed.
   */
  val resolveFailed: LogMarker =
    LogMarker("akkaBootstrapResolveFailed")

  /**
   * Marker "akkaBootstrapInProgress" of log event when bootstrap is in progress.
   * @param contactPoints The hostname and port of the resolved and selected contact points. Included as property "akkaContactPoints".
   * @param seedNodes The address of the observed seed nodes of the Akka Cluster. Included as property "akkaSeedNodes".
   */
  def inProgress(contactPoints: Set[String], seedNodes: Set[Address]): LogMarker =
    LogMarker("akkaBootstrapInProgress", Map(Properties.ContactPoints -> contactPoints.mkString(", "),
      Properties.SeedNodes -> seedNodes.mkString(", ")))

  /**
   * Marker "akkaBootstrapSeedNodes" of log event when seed nodes of the Akka Cluster have been discovered.
   * @param seedNodes The address of the observed seed nodes of the Akka Cluster. Included as property "akkaSeedNodes".
   */
  def seedNodes(seedNodes: Set[Address]): LogMarker =
    LogMarker("akkaBootstrapSeedNodes", Map(Properties.SeedNodes -> seedNodes.mkString(", ")))

  /**
   * Marker "akkaBootstrapJoin" of log event when joining the seed nodes of an existing Akka Cluster.
   * @param seedNodes The address of the seed nodes of the Akka Cluster. Included as property "akkaSeedNodes".
   */
  def join(seedNodes: Set[Address]): LogMarker =
    LogMarker("akkaBootstrapJoin", Map(Properties.SeedNodes -> seedNodes.mkString(", ")))

  /**
   * Marker "akkaBootstrapJoinSelf" of log event when joining self to form a new Akka Cluster.
   */
  val joinSelf: LogMarker =
    LogMarker("akkaBootstrapJoinSelf")

  /**
   * Marker "akkaBootstrapResolveFailed" of log event when resolve of contact points failed.
   * @param contactPoints The hostname and port of the resolved and selected contact points. Included as property "akkaContactPoints".
   */
  def seedNodesProbingFailed(contactPoints: Iterable[String]): LogMarker =
    LogMarker("akkaBootstrapSeedNodesProbingFailed", Map(Properties.ContactPoints -> contactPoints.mkString(", ")))

}
