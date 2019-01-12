/*
 * Copyright (C) 2017-2018 Lightbend Inc. <http://www.lightbend.com>
 */

package akka.management.cluster

import akka.annotation.InternalApi
import akka.cluster.{ Cluster, MemberStatus }
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.management.cluster.ClusterHealthCheckRoutes.ClusterHealthCheckSettings
import akka.util.Helpers
import com.typesafe.config.Config

import scala.collection.JavaConverters._

/**
 * INTERNAL API
 */
@InternalApi
private[akka] object ClusterHealthCheckRoutes {

  def apply(cluster: Cluster): Route = {
    new ClusterHealthCheckRoutes(
        new ClusterHealthCheckSettings(
            cluster.system.settings.config.getConfig("akka.management.cluster.http.healthcheck")),
        () => cluster.selfMember.status).routes
  }

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

/**
 * INTERNAL API
 */
@InternalApi private[akka] class ClusterHealthCheckRoutes(settings: ClusterHealthCheckSettings,
                                                          status: () => MemberStatus) {

  val routes: Route = {

    concat(
      path("ready") {
        get {
          val selfState = status()
          if (settings.readyStates.contains(selfState)) complete(StatusCodes.OK)
          else complete(StatusCodes.InternalServerError)
        }
      },
      path("alive") {
        get {
          // When Akka HTTP can respond to requests, that is sufficient
          // to consider ourselves 'live': we don't want the process to be killed
          // when we're in the process of shutting down (only stop sending
          // us traffic, which is done due to the readiness check then failing)
          complete(StatusCodes.OK)
        }
      }
    )
  }

}
