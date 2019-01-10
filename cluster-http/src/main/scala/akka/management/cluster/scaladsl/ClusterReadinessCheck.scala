/*
 * Copyright (C) 2017-2018 Lightbend Inc. <http://www.lightbend.com>
 */

package akka.management.cluster.scaladsl

import akka.actor.ActorSystem
import akka.annotation.InternalApi
import akka.cluster.{ Cluster, MemberStatus }
import akka.util.Helpers
import com.typesafe.config.Config

import scala.collection.JavaConverters._
import scala.concurrent.Future

/**
 * Internal API
 */
@InternalApi
private[akka] object ClusterReadinessCheckSettings {
  def memberStatus(status: String): MemberStatus =
    Helpers.toRootLowerCase(status) match {
      case "weaklyup" => MemberStatus.WeaklyUp
      case "up" => MemberStatus.Up
      case "exiting" => MemberStatus.Exiting
      case "down" => MemberStatus.Down
      case "joining" => MemberStatus.Joining
      case "leaving" => MemberStatus.Leaving
      case "removed" => MemberStatus.Removed
      case invalid =>
        throw new IllegalArgumentException(
          s"'$invalid' is not a valid MemberStatus. See reference.conf for valid values"
        )
    }
  def apply(config: Config): ClusterReadinessCheckSettings =
    new ClusterReadinessCheckSettings(config.getStringList("ready-states").asScala.map(memberStatus).toSet)
}

final class ClusterReadinessCheckSettings(val readyStates: Set[MemberStatus])

final class ClusterReadinessCheck @InternalApi private[akka] (system: ActorSystem,
                                                              selfStatus: () => MemberStatus,
                                                              settings: ClusterReadinessCheckSettings)
    extends (() => Future[Boolean]) {

  def this(system: ActorSystem) =
    this(system, () => Cluster(system).selfMember.status,
      ClusterReadinessCheckSettings(system.settings.config.getConfig("akka.management.cluster.http.healthcheck")))

  override def apply(): Future[Boolean] = {
    Future.successful(settings.readyStates.contains(selfStatus()))
  }
}
