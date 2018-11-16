/*
 * Copyright (C) 2017 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.management.cluster

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.DefaultJsonProtocol

final case class ClusterUnreachableMember(node: String, observedBy: Seq[String])
final case class ClusterMember(node: String, nodeUid: String, status: String, roles: Set[String])
final case class ClusterMembers(selfNode: String,
                                members: Set[ClusterMember],
                                unreachable: Seq[ClusterUnreachableMember],
                                leader: Option[String],
                                oldest: Option[String],
                                oldestPerRole: Map[String, String])
final case class ClusterHttpManagementMessage(message: String)
final case class ShardRegionInfo(shardId: String, numEntities: Int)
final case class ShardDetails(regions: Seq[ShardRegionInfo])

private[akka] sealed trait ClusterHttpManagementOperation
private[akka] case object Down extends ClusterHttpManagementOperation
private[akka] case object Leave extends ClusterHttpManagementOperation
private[akka] case object Join extends ClusterHttpManagementOperation

object ClusterHttpManagementOperation {
  def fromString(value: String): Option[ClusterHttpManagementOperation] =
    Vector(Down, Leave, Join).find(_.toString.equalsIgnoreCase(value))
}

trait ClusterHttpManagementJsonProtocol extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val clusterUnreachableMemberFormat = jsonFormat2(ClusterUnreachableMember)
  implicit val clusterMemberFormat = jsonFormat4(ClusterMember)
  implicit val clusterMembersFormat = jsonFormat6(ClusterMembers)
  implicit val clusterMemberMessageFormat = jsonFormat1(ClusterHttpManagementMessage)
  implicit val shardRegionInfoFormat = jsonFormat2(ShardRegionInfo)
  implicit val shardDetailsFormat = jsonFormat1(ShardDetails)
}
