/*
 * Copyright (C) 2017 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.cluster.http.management

import akka.actor.AddressFromURIString
import akka.cluster.{ Cluster, Member, MemberStatus }
import akka.cluster.sharding.{ ClusterSharding, ShardRegion }
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import akka.pattern.AskTimeoutException
import akka.util.Timeout

import akka.pattern.ask
import scala.concurrent.duration._

object ClusterHttpManagementRoutes extends ClusterHttpManagementHelper {
  import akka.http.scaladsl.server.Directives._

  private def routeGetMembers(cluster: Cluster) =
    get {
      complete {
        val members = cluster.readView.state.members.map(memberToClusterMember)

        val unreachable = cluster.readView.reachability.observersGroupedByUnreachable.toSeq.sortBy(_._1).map {
          case (subject, observers) ⇒
            ClusterUnreachableMember(s"${subject.address}", observers.toSeq.sorted.map(m ⇒ s"${m.address}"))
        }

        val leader = cluster.readView.leader.map(_.toString)
        val oldest = cluster.state.members.toSeq
          .filter(node => node.status == MemberStatus.Up && node.dataCenter == cluster.selfDataCenter)
          .sorted(Member.ageOrdering)
          .headOption // we are only interested in the oldest one that is still Up
          .map(_.address.toString)

        ClusterMembers(s"${cluster.readView.selfAddress}", members, unreachable, leader, oldest)
      }
    }

  private def routePostMembers(cluster: Cluster) =
    post {
      formField('address) { addressString ⇒
        complete {
          val address = AddressFromURIString(addressString)
          cluster.join(address)
          ClusterHttpManagementMessage(s"Joining $address")
        }
      }
    }

  private def routeGetMember(cluster: Cluster, member: Member) =
    get {
      complete {
        memberToClusterMember(member)
      }
    }

  private def routeDeleteMember(cluster: Cluster, member: Member) =
    delete {
      complete {
        cluster.leave(member.uniqueAddress.address)
        ClusterHttpManagementMessage(s"Leaving ${member.uniqueAddress.address}")
      }
    }

  private def routePutMember(cluster: Cluster, member: Member) =
    put {
      formField('operation) { operation ⇒
        ClusterHttpManagementOperation.fromString(operation) match {
          case Some(Down) ⇒
            cluster.down(member.uniqueAddress.address)
            complete(ClusterHttpManagementMessage(s"Downing ${member.uniqueAddress.address}"))
          case Some(Leave) ⇒
            cluster.leave(member.uniqueAddress.address)
            complete(ClusterHttpManagementMessage(s"Leaving ${member.uniqueAddress.address}"))
          case _ ⇒
            complete(StatusCodes.BadRequest → ClusterHttpManagementMessage("Operation not supported"))
        }
      }
    }

  private def findMember(cluster: Cluster, memberAddress: String): Option[Member] =
    cluster.readView.members.find(
        m ⇒ s"${m.uniqueAddress.address}" == memberAddress || m.uniqueAddress.address.hostPort == memberAddress)

  private def routesMember(cluster: Cluster) =
    path(Remaining) { memberAddress ⇒
      findMember(cluster, memberAddress) match {
        case Some(member) ⇒
          routeGetMember(cluster, member) ~ routeDeleteMember(cluster, member) ~ routePutMember(cluster, member)
        case None ⇒
          complete(StatusCodes.NotFound → ClusterHttpManagementMessage(s"Member [$memberAddress] not found"))
      }
    }

  private def routeGetShardInfo(cluster: Cluster, shardRegionName: String) =
    get {
      extractExecutionContext { implicit executor =>
        complete {
          implicit val timeout = Timeout(5.seconds)
          try {
            ClusterSharding(cluster.system)
              .shardRegion(shardRegionName)
              .ask(ShardRegion.GetShardRegionStats)
              .mapTo[ShardRegion.ShardRegionStats]
              .map { shardRegionStats =>
                ShardDetails(shardRegionStats.stats.map(s => ShardRegionInfo(s._1, s._2)).toSeq)
              }
          } catch {
            case _: AskTimeoutException =>
              StatusCodes.NotFound → ClusterHttpManagementMessage(
                  s"Shard Region $shardRegionName not responding, may have been terminated")
            case _: IllegalArgumentException =>
              StatusCodes.NotFound → ClusterHttpManagementMessage(s"Shard Region $shardRegionName is not started")
          }
        }
      }
    }

  /**
   * Creates an instance of [[akka.cluster.http.management.ClusterHttpManagementRoutes]] to manage the specified
   * [[akka.cluster.Cluster]] instance. This version does not provide Basic Authentication. It uses
   * the default path "members".
   */
  def apply(cluster: Cluster): Route = apply(cluster, "")

  /**
   * Creates an instance of [[akka.cluster.http.management.ClusterHttpManagementRoutes]] to manage the specified
   * [[akka.cluster.Cluster]] instance. This version does not provide Basic Authentication. It uses
   * the specified path `pathPrefixName`.
   */
  def apply(cluster: Cluster, pathPrefixName: String): Route = {
    val basePath = if (pathPrefixName.isEmpty) rawPathPrefix(pathPrefixName) else pathPrefix(pathPrefixName)

    basePath {
      pathPrefix("members") {
        pathEndOrSingleSlash {
          routeGetMembers(cluster) ~ routePostMembers(cluster)
        } ~
        routesMember(cluster)
      } ~
      pathPrefix("shards" / Remaining) { shardRegionName =>
        pathEnd {
          routeGetShardInfo(cluster, shardRegionName)
        }
      }
    }
  }

  /**
   * Creates an instance of [[akka.cluster.http.management.ClusterHttpManagementRoutes]] to manage the specified
   * [[akka.cluster.Cluster]] instance. This version provides Basic Authentication through the specified
   * AsyncAuthenticator. It uses the default path "members".
   */
  def apply(cluster: Cluster, asyncAuthenticator: AsyncAuthenticator[String]): Route =
    authenticateBasicAsync[String](realm = "secured", asyncAuthenticator) { _ ⇒
      apply(cluster)
    }

  /**
   * Creates an instance of [[akka.cluster.http.management.ClusterHttpManagementRoutes]] to manage the specified
   * [[akka.cluster.Cluster]] instance. This version provides Basic Authentication through the specified
   * AsyncAuthenticator. It uses the specified path `pathPrefixName`.
   */
  def apply(cluster: Cluster, pathPrefixName: String, asyncAuthenticator: AsyncAuthenticator[String]): Route =
    authenticateBasicAsync[String](realm = "secured", asyncAuthenticator) { _ ⇒
      apply(cluster, pathPrefixName)
    }
}
