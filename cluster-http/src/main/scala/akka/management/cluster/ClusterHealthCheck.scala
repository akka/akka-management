/*
 * Copyright (C) 2017-2018 Lightbend Inc. <http://www.lightbend.com>
 */

package akka.management.cluster
import akka.actor.ExtendedActorSystem
import akka.cluster.{Cluster, MemberStatus}
import akka.util.Helpers
import com.sun.corba.se.impl.orbutil.closure.Future
import com.typesafe.config.Config

object ClusterHealthCheck {
  def memberStatus(status: String): MemberStatus = Helpers.toRootLowerCase(status) match {
    case "weaklyup" => MemberStatus.WeaklyUp
    case "up" => MemberStatus.Up
    case "exiting" => MemberStatus.Exiting
    case "down" => MemberStatus.Down
    case "joining" => MemberStatus.Joining
    case "leaving" => MemberStatus.Leaving
    case "removed" => MemberStatus.Removed
    case invalid =>
      throw new IllegalArgumentException(
          s"'$invalid' is not a valid MemberStatus. See reference.conf for valid values")
  }

  class ClusterHealthCheckSettings(config: Config) {
    val readyStates: Set[MemberStatus] = config.getStringList("ready-states").asScala.map(memberStatus).toSet
  }
}

final class ClusterHealthCheck(system: ExtendedActorSystem) extends (() => Future[Boolean]){

  private val cluster = Cluster(system)
  private val settings = new ClusterHealthCheckSettings(system.settings.config.getConfig("akka.management.cluster.http.healthcheck"))

  override def apply(): Future[Boolean] = {
    val selfState = cluster.selfMember.status
    Future.successful(settings.readyStates.contains(selfState))
  }
}
