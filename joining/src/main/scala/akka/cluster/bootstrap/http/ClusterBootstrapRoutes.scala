/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.cluster.bootstrap.http

import akka.actor.Address
import akka.cluster.{ Cluster, Member, MemberStatus }
import akka.cluster.bootstrap.ClusterBootstrapSettings
import akka.cluster.bootstrap.http.HttpBootstrapJsonProtocol.{ ClusterMember, SeedNodes }
import akka.http.scaladsl.model.{ HttpRequest, Uri }
import akka.http.scaladsl.server.Route

final class ClusterBootstrapRoutes(settings: ClusterBootstrapSettings) extends HttpBootstrapJsonProtocol {
  import akka.http.scaladsl.server.Directives._

  private def routeGetSeedNodes: Route = extractActorSystem { implicit system ⇒
    import system.dispatcher
    val cluster = Cluster(system)

    def memberToClusterMember(m: Member): ClusterMember =
      ClusterMember(m.uniqueAddress.address, m.uniqueAddress.longUid, m.status.toString, m.roles)

    val readView = cluster.readView
    val members = readView.state.members.take(settings.httpMaxSeedNodesToExpose).map(memberToClusterMember)

    val oldest = cluster.state.members.toSeq
      .filter(node => node.status == MemberStatus.Up && node.dataCenter == cluster.selfDataCenter)
      .sorted(Member.ageOrdering)
      .headOption // we are only interested in the oldest one that is still Up
      .map(_.uniqueAddress.address)

    val info = SeedNodes(cluster.selfMember.uniqueAddress.address, members, oldest)
    system.log.info("Replying: " + info)
    complete(info)
  }

  // TODO ip whitelist feature?
  val routes = {
    // TODO basePath, same as akka-management
    // val basePath = if (pathPrefixName.isEmpty) rawPathPrefix(pathPrefixName) else pathPrefix(pathPrefixName)

    logRequest("bootstrap") {
      concat(
        (get & path("bootstrap" / "seed-nodes"))(routeGetSeedNodes)
      )
    }
  }

}

object ClusterBootstrapRequests {
  import akka.http.scaladsl.client.RequestBuilding._

  def bootstrapSeedNodes(baseUri: Uri, selfNodeAddress: Address): HttpRequest =
    Get(baseUri + "/bootstrap" + "/seed-nodes")

}
