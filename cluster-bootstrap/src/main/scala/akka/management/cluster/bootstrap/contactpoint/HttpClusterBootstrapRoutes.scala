/*
 * Copyright (C) 2017 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.management.cluster.bootstrap.contactpoint

import akka.actor.ActorSystem
import akka.cluster.{ Cluster, Member }
import akka.event.{ Logging, LoggingAdapter }
import akka.http.scaladsl.model.{ HttpRequest, Uri }
import akka.http.scaladsl.server.Route
import akka.management.cluster.bootstrap.ClusterBootstrapSettings
import akka.management.cluster.bootstrap.contactpoint.HttpBootstrapJsonProtocol.{ ClusterMember, SeedNodes }

import scala.concurrent.duration._

final class HttpClusterBootstrapRoutes(settings: ClusterBootstrapSettings) extends HttpBootstrapJsonProtocol {

  import akka.http.scaladsl.server.Directives._

  private def routeGetSeedNodes: Route = extractClientIP { clientIp ⇒
    extractActorSystem { implicit system ⇒
      import akka.cluster.MemberStatus
      val cluster = Cluster(system)

      def memberToClusterMember(m: Member): ClusterMember =
        ClusterMember(m.uniqueAddress.address, m.uniqueAddress.longUid, m.status.toString, m.roles)

      val state = cluster.state

      // TODO shuffle the members so in a big deployment nodes start joining different ones and not all the same?
      val members = state.members
        .diff(state.unreachable)
        .filter(
            m => m.status == MemberStatus.up || m.status == MemberStatus.weaklyUp || m.status == MemberStatus.joining)
        .take(settings.contactPoint.httpMaxSeedNodesToExpose)
        .map(memberToClusterMember)

      val info = SeedNodes(cluster.selfMember.uniqueAddress.address, members)
      log.info("Bootstrap request from {}: Contact Point returning {} seed-nodes ([{}])", clientIp, members.size,
        members)
      complete(info)
    }
  }

  val routes: Route =
    (get & path("bootstrap" / "seed-nodes")) {
      toStrictEntity(1.second) { // always drain everything
        routeGetSeedNodes
      }
    }

  private def log(implicit sys: ActorSystem): LoggingAdapter =
    Logging(sys, classOf[HttpClusterBootstrapRoutes])

}

object ClusterBootstrapRequests {

  import akka.http.scaladsl.client.RequestBuilding._

  def bootstrapSeedNodes(baseUri: Uri): HttpRequest =
    Get(baseUri + "/bootstrap/seed-nodes")

}
