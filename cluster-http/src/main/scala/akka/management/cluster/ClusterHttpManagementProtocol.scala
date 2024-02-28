/*
 * Copyright (C) 2017-2023 Lightbend Inc. <https://www.lightbend.com>
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
final case class ShardDetails(regions: immutable.Seq[ShardRegionInfo])

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
  implicit val shardDetailsFormat: RootJsonFormat[ShardDetails] = jsonFormat1(ShardDetails.apply)
}
