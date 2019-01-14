/*
 * Copyright (C) 2017-2018 Lightbend Inc. <http://www.lightbend.com>
 */

package akka.management.cluster

import scala.collection.immutable

import akka.annotation.InternalApi
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.DefaultJsonProtocol
import spray.json.RootJsonFormat

final case class ClusterUnreachableMember(node: String, observedBy: immutable.Seq[String])
final case class ClusterMember(node: String, nodeUid: String, status: String, roles: Set[String])
final case class ClusterMembers(selfNode: String,
                                members: Set[ClusterMember],
                                unreachable: immutable.Seq[ClusterUnreachableMember],
                                leader: Option[String],
                                oldest: Option[String],
                                oldestPerRole: Map[String, String])
final case class ClusterHttpManagementMessage(message: String)
final case class ShardRegionInfo(shardId: String, numEntities: Int)
final case class ShardDetails(regions: immutable.Seq[ShardRegionInfo])

/** INTERNAL API */
@InternalApi private[akka] sealed trait ClusterHttpManagementOperation

/** INTERNAL API */
@InternalApi private[akka] case object Down extends ClusterHttpManagementOperation

/** INTERNAL API */
@InternalApi private[akka] case object Leave extends ClusterHttpManagementOperation

/** INTERNAL API */
@InternalApi private[akka] case object Join extends ClusterHttpManagementOperation

/** INTERNAL API */
@InternalApi private[akka] object ClusterHttpManagementOperation {
  def fromString(value: String): Option[ClusterHttpManagementOperation] =
    Vector(Down, Leave, Join).find(_.toString.equalsIgnoreCase(value))
}

trait ClusterHttpManagementJsonProtocol extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val clusterUnreachableMemberFormat: RootJsonFormat[ClusterUnreachableMember] =
    jsonFormat2(ClusterUnreachableMember)
  implicit val clusterMemberFormat: RootJsonFormat[ClusterMember] = jsonFormat4(ClusterMember)
  implicit val clusterMembersFormat: RootJsonFormat[ClusterMembers] = jsonFormat6(ClusterMembers)
  implicit val clusterMemberMessageFormat: RootJsonFormat[ClusterHttpManagementMessage] =
    jsonFormat1(ClusterHttpManagementMessage)
  implicit val shardRegionInfoFormat: RootJsonFormat[ShardRegionInfo] = jsonFormat2(ShardRegionInfo)
  implicit val shardDetailsFormat: RootJsonFormat[ShardDetails] = jsonFormat1(ShardDetails)
}
