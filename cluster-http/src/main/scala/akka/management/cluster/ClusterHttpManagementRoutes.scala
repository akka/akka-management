/*
 * Copyright (C) 2017-2018 Lightbend Inc. <http://www.lightbend.com>
 */

package akka.management.cluster

import akka.actor.AddressFromURIString
import akka.cluster.sharding.{ ClusterSharding, ShardRegion }
import akka.cluster.{ Cluster, Member, MemberStatus }
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import akka.pattern.{ ask, AskTimeoutException }
import akka.util.Timeout

import scala.concurrent.duration._

object ClusterHttpManagementRoutes extends ClusterHttpManagementJsonProtocol {
  import ClusterHttpManagementHelper._

  import akka.http.scaladsl.server.Directives._

  private def routeGetMembers(cluster: Cluster): Route =
    get {
      complete {
        val readView = ClusterReadViewAccess.internalReadView(cluster)
        val members = readView.state.members.map(memberToClusterMember)

        val unreachable = readView.reachability.observersGroupedByUnreachable.toVector.sortBy(_._1).map {
          case (subject, observers) ⇒
            ClusterUnreachableMember(s"${subject.address}", observers.toVector.sorted.map(m ⇒ s"${m.address}"))
        }

        val thisDcMembers =
          cluster.state.members.toSeq.filter(
              node => node.status == MemberStatus.Up && node.dataCenter == cluster.selfDataCenter)

        val leader = readView.leader.map(_.toString)

        val oldest = if (thisDcMembers.isEmpty) None else Some(thisDcMembers.min(Member.ageOrdering).address.toString)

        ClusterMembers(s"${readView.selfAddress}", members, unreachable, leader, oldest, oldestPerRole(thisDcMembers))
      }
    }

  private def routePostMembers(cluster: Cluster): Route =
    post {
      formField('address) { addressString ⇒
        complete {
          val address = AddressFromURIString(addressString)
          cluster.join(address)
          ClusterHttpManagementMessage(s"Joining $address")
        }
      }
    }

  private def routeGetMember(cluster: Cluster, member: Member): Route =
    get {
      complete {
        memberToClusterMember(member)
      }
    }

  private def routeDeleteMember(cluster: Cluster, member: Member): Route =
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

  private def findMember(cluster: Cluster, memberAddress: String): Option[Member] = {
    val readView = ClusterReadViewAccess.internalReadView(cluster)
    readView.members.find(
        m ⇒ s"${m.uniqueAddress.address}" == memberAddress || m.uniqueAddress.address.hostPort == memberAddress)
  }

  private def routeFindMember(cluster: Cluster) =
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
                ShardDetails(shardRegionStats.stats.map(s => ShardRegionInfo(s._1, s._2)).toVector)
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
   * Creates an instance of [[ClusterHttpManagementRoutes]] to manage the specified
   * [[akka.cluster.Cluster]] instance. This version does not provide Basic Authentication.
   */
  def apply(cluster: Cluster): Route =
    concat(
      pathPrefix("cluster" / "members") {
        concat(
          pathEndOrSingleSlash {
            routeGetMembers(cluster) ~ routePostMembers(cluster)
          },
          routeFindMember(cluster)
        )
      },
      pathPrefix("cluster" / "shards" / Remaining) { shardRegionName =>
        routeGetShardInfo(cluster, shardRegionName)
      }
    )

  /**
   * Creates an instance of [[ClusterHttpManagementRoutes]] with only the read only routes.
   */
  def readOnly(cluster: Cluster): Route =
    concat(
      pathPrefix("cluster" / "members") {
        concat(
          pathEndOrSingleSlash {
            routeGetMembers(cluster)
          },
          routeFindMember(cluster)
        )
      },
      pathPrefix("cluster" / "shards" / Remaining) { shardRegionName =>
        routeGetShardInfo(cluster, shardRegionName)
      }
    )
}
