/*
 * Copyright (C) 2017 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.cluster.bootstrap.contactpoint

import java.util.concurrent.atomic.AtomicReference

import akka.actor.{ ActorSystem, Address }
import akka.annotation.InternalApi
import akka.cluster.{ Cluster, Member, MemberStatus, UniqueAddress }
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
      val cluster = Cluster(system)

      def memberToClusterMember(m: Member): ClusterMember =
        ClusterMember(m.uniqueAddress.address, m.uniqueAddress.longUid, m.status.toString, m.roles)

      val state = cluster.state

      // TODO shuffle the members so in a big deployment nodes start joining different ones and not all the same?
      val members = state.members.take(settings.contactPoint.httpMaxSeedNodesToExpose).map(memberToClusterMember)

      val info = SeedNodes(cluster.selfMember.uniqueAddress.address, members)
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
