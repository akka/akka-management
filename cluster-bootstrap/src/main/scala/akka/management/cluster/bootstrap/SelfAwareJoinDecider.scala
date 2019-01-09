/*
 * Copyright (C) 2017-2018 Lightbend Inc. <http://www.lightbend.com>
 */

package akka.management.cluster.bootstrap

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Try

import akka.actor.ActorSystem
import akka.annotation.InternalApi
import akka.discovery.ServiceDiscovery.ResolvedTarget
import akka.event.Logging

/**
 * INTERNAL API
 *
 * Class for further behavior in a [[akka.management.cluster.bootstrap.JoinDecider]]
 * leveraging self host logic.
 */
@InternalApi private[bootstrap] abstract class SelfAwareJoinDecider(system: ActorSystem,
                                                                    settings: ClusterBootstrapSettings)
    extends JoinDecider {

  protected val log = Logging(system, getClass)

  /** Returns the current `selfContactPoints` as a String for logging, e.g. [127.0.0.1:64714]. */
  protected def contactPointsString(contactPoints: Set[(String, Int)]): String =
    contactPoints.map(_.productIterator.mkString(":")).mkString("[", ",", "]")

  /**
   * The value `ClusterBootstrap(system).selfContactPoints` is set prior to HTTP binding,
   * during [[akka.management.AkkaManagement.start()]], hence we accept blocking on
   * this initialization.
   */
  private[bootstrap] def selfContactPoints: Set[(String, Int)] =
    Try(Await.result(ClusterBootstrap(system).selfContactPoints, 10.seconds))
      .getOrElse(throw new IllegalStateException(
            "'Bootstrap.selfContactPoint' was NOT set, but is required for the bootstrap to work " +
            "if binding bootstrap routes manually and not via akka-management."))

  /**
   * Determines whether it has the need and ability to join self and create a new cluster.
   */
  private[bootstrap] def canJoinSelf(target: ResolvedTarget, info: SeedNodesInformation): Boolean = {
    val self = selfContactPoints
    if (matchesSelf(target, self)) true
    else {
      if (!info.contactPoints.exists(matchesSelf(_, self))) {
        log.warning("Self contact point [{}] not found in targets {}", contactPointsString(selfContactPoints),
          info.contactPoints.mkString(", "))
      }
      false
    }
  }

  private[bootstrap] def matchesSelf(target: ResolvedTarget, contactPoints: Set[(String, Int)]): Boolean =
    target.port match {
      case None =>
        contactPoints.exists { case (host, _) => hostMatches(host, target) }
      case Some(lowestPort) =>
        contactPoints.exists { case (host, port) => hostMatches(host, target) && port == lowestPort }
    }

  /**
   * Checks for both host name and IP address for discovery mechanisms that return both.
   */
  protected def hostMatches(host: String, target: ResolvedTarget): Boolean =
    host == target.host || target.address.map(_.getHostAddress).contains(host)

}
