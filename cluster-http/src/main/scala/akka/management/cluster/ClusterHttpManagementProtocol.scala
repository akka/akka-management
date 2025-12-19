/*
 * Copyright (C) 2017-2025 Lightbend Inc. <https://akka.io>
 */

package akka.management.cluster

import scala.collection.immutable

import akka.annotation.InternalApi
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.DefaultJsonProtocol
import spray.json.RootJsonFormat

final case class ClusterUnreachableMember(node: String, observedBy: immutable.Seq[String])
final case class ClusterMember(node: String, nodeUid: String, status: String, roles: Set[String])
object ClusterMember {
  implicit val clusterMemberOrdering: Ordering[ClusterMember] = Ordering.by(_.node)
}
final case class ClusterMembers(
    selfNode: String,
    members: Set[ClusterMember],
    unreachable: immutable.Seq[ClusterUnreachableMember],
    leader: Option[String],
    oldest: Option[String],
    oldestPerRole: Map[String, String])
final case class ClusterHttpManagementMessage(message: String)
final case class ShardEntityTypeKeys(entityTypeKeys: immutable.Set[String])
final case class ShardRegionInfo(shardId: String, numEntities: Int)
final case class ShardDetails(regions: immutable.Seq[ShardRegionInfo], failed: immutable.Set[String] = Set.empty)
final case class ClusterShardingNodeStats(shards: immutable.Seq[ShardRegionInfo], failed: immutable.Set[String])
final case class ClusterShardingStatsResponse(
    regions: Map[String, ClusterShardingNodeStats],
    totalEntities: Int,
    totalShards: Int)
final case class ShardStateInfo(shardId: String, entityIds: immutable.Set[String])
final case class ShardRegionStateResponse(shards: immutable.Seq[ShardStateInfo], failed: immutable.Set[String])

/** INTERNAL API */
@InternalApi private[akka] sealed trait ClusterHttpManagementMemberOperation

/** INTERNAL API */
@InternalApi private[akka] case object Down extends ClusterHttpManagementMemberOperation

/** INTERNAL API */
@InternalApi private[akka] case object Leave extends ClusterHttpManagementMemberOperation

/** INTERNAL API */
@InternalApi private[akka] case object Join extends ClusterHttpManagementMemberOperation

/** INTERNAL API */
@InternalApi private[akka] object ClusterHttpManagementMemberOperation {
  def fromString(value: String): Option[ClusterHttpManagementMemberOperation] =
    Vector(Down, Leave, Join).find(_.toString.equalsIgnoreCase(value))
}

trait ClusterHttpManagementJsonProtocol extends SprayJsonSupport with DefaultJsonProtocol {
  // If adding more formats here, remember to also add in META-INF/native-image reflect config
  implicit val clusterUnreachableMemberFormat: RootJsonFormat[ClusterUnreachableMember] =
    jsonFormat2(ClusterUnreachableMember.apply)
  implicit val clusterMemberFormat: RootJsonFormat[ClusterMember] = jsonFormat4(ClusterMember.apply)
  implicit val clusterMembersFormat: RootJsonFormat[ClusterMembers] = jsonFormat6(ClusterMembers.apply)
  implicit val clusterMemberMessageFormat: RootJsonFormat[ClusterHttpManagementMessage] =
    jsonFormat1(ClusterHttpManagementMessage.apply)
  implicit val shardEntityTypeKeysFormat: RootJsonFormat[ShardEntityTypeKeys] = jsonFormat1(ShardEntityTypeKeys.apply)
  implicit val shardRegionInfoFormat: RootJsonFormat[ShardRegionInfo] = jsonFormat2(ShardRegionInfo.apply)
  implicit val shardDetailsFormat: RootJsonFormat[ShardDetails] = jsonFormat2(ShardDetails.apply)
  implicit val clusterShardingNodeStatsFormat: RootJsonFormat[ClusterShardingNodeStats] =
    jsonFormat2(ClusterShardingNodeStats.apply)
  implicit val clusterShardingStatsResponseFormat: RootJsonFormat[ClusterShardingStatsResponse] =
    jsonFormat3(ClusterShardingStatsResponse.apply)
  implicit val shardStateInfoFormat: RootJsonFormat[ShardStateInfo] = jsonFormat2(ShardStateInfo.apply)
  implicit val shardRegionStateResponseFormat: RootJsonFormat[ShardRegionStateResponse] =
    jsonFormat2(ShardRegionStateResponse.apply)
}
