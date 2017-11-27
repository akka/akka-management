/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.cluster.bootstrap.contactpoint

import akka.actor.{ ActorSystem, Address }
import akka.cluster.{ Cluster, Member, MemberStatus }
import akka.cluster.bootstrap.ClusterBootstrapSettings
import akka.cluster.bootstrap.contactpoint.HttpBootstrapJsonProtocol.{ ClusterMember, SeedNodes }
import akka.event.{ Logging, LoggingAdapter }
import akka.http.scaladsl.model.{ HttpRequest, Uri }
import akka.http.scaladsl.server.Route

import scala.concurrent.duration._

final class HttpClusterBootstrapRoutes(settings: ClusterBootstrapSettings) extends HttpBootstrapJsonProtocol {

  import akka.http.scaladsl.server.Directives._

  private def routeGetSeedNodes: Route = extractClientIP { clientIp ⇒
    extractActorSystem { implicit system ⇒
      import system.dispatcher
      val cluster = Cluster(system)

      def memberToClusterMember(m: Member): ClusterMember =
        ClusterMember(m.uniqueAddress.address, m.uniqueAddress.longUid, m.status.toString, m.roles)

      val state = cluster.state

      // TODO shuffle the members so in a big deployment nodes start joining different ones and not all the same?
      val members = state.members.take(settings.contactPoint.httpMaxSeedNodesToExpose).map(memberToClusterMember)

      // TODO add a method to find oldest to cluster state?
      val oldest = state.members.toSeq
        .filter(node => node.status == MemberStatus.Up && node.dataCenter == cluster.selfDataCenter)
        .sorted(Member.ageOrdering)
        .headOption // we are only interested in the oldest one that is still Up
        .map(_.uniqueAddress.address)

      val info = SeedNodes(cluster.selfMember.uniqueAddress.address, members, oldest)
      log.info("Bootstrap request from {}: Contact Point returning {} seed-nodes ([{}])", clientIp, members.size,
        members)
      complete(info)
    }
  }

  // TODO ip whitelist feature?
  val routes = {
    // TODO basePath, same as akka-management
    // val basePath = if (pathPrefixName.isEmpty) rawPathPrefix(pathPrefixName) else pathPrefix(pathPrefixName)

    toStrictEntity(1.second) { // always drain everything
      concat(
        (get & path("bootstrap" / "seed-nodes"))(routeGetSeedNodes)
      )
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
