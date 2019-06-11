/*
 * Copyright (C) 2017-2018 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.management.cluster.bootstrap.internal

import akka.actor.ActorSystem
import akka.annotation.InternalApi
import akka.cluster.{Cluster, Member, MemberStatus}
import akka.event.LoggingAdapter
import akka.management.cluster.bootstrap.ClusterBootstrapSettings
import akka.management.cluster.bootstrap.contactpoint.HttpBootstrapJsonProtocol.{ClusterMember, SeedNodes}

@InternalApi
private[bootstrap] class ContactPoint(system: ActorSystem, settings: ClusterBootstrapSettings, log: LoggingAdapter) {

  private val cluster = Cluster(system)

  def seedNodes(clientAddress: String): SeedNodes = {
    def memberToClusterMember(m: Member): ClusterMember =
      ClusterMember(m.uniqueAddress.address, m.uniqueAddress.longUid, m.status.toString, m.roles)

    val state = cluster.state

    // TODO shuffle the members so in a big deployment nodes start joining different ones and not all the same?
    val members = state.members
      .diff(state.unreachable)
      .filter(
        m => m.status == MemberStatus.up || m.status == MemberStatus.weaklyUp || m.status == MemberStatus.joining)
      .take(settings.contactPoint.httpMaxSeedNodesToExpose)
      .map(memberToClusterMember)

    val info = SeedNodes(cluster.selfMember.uniqueAddress.address, members)
    log.info("Bootstrap request from {}: Contact Point returning {} seed-nodes ([{}])", clientAddress, members.size,
      members)
    info
  }
}
