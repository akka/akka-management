/*
 * Copyright (C) 2017-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.management.cluster

import akka.cluster.Member

object ClusterHttpManagementHelper {
  def memberToClusterMember(m: Member): ClusterMember =
    ClusterMember(s"${m.address}", s"${m.uniqueAddress.longUid}", s"${m.status}", m.roles)

  private[akka] def oldestPerRole(thisDcMembers: Seq[Member]): Map[String, String] = {
    val roles: Set[String] = thisDcMembers.flatMap(_.roles).toSet
    roles.map(role => (role, oldestForRole(thisDcMembers, role))).toMap
  }

  private def oldestForRole(cluster: Seq[Member], role: String): String = {
    val forRole = cluster.filter(_.roles.contains(role))

    if (forRole.isEmpty)
      "<unknown>"
    else
      forRole.min(Member.ageOrdering).address.toString

  }
}
