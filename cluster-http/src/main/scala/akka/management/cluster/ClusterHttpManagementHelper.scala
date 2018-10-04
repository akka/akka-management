/*
 * Copyright (C) 2017 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.management.cluster

import akka.cluster.Member

object ClusterHttpManagementHelper {
  def memberToClusterMember(m: Member): ClusterMember =
    ClusterMember(s"${m.uniqueAddress.address}", s"${m.uniqueAddress.longUid}", s"${m.status}", m.roles)

  private[akka] def oldestPerRole(thisDcMembers: Seq[Member]): Map[String, String] = {
    val roles: Set[String] = thisDcMembers.flatMap(_.roles).toSet
    roles.map(role => (role, oldestForRole(thisDcMembers, role))).toMap
  }

  private def oldestForRole(cluster: Seq[Member], role: String): String = {
    cluster
      .filter(_.roles.contains(role))
      .sorted(Member.ageOrdering)
      .headOption
      .map(_.address.toString)
      .getOrElse("<unknown>")

  }
}
