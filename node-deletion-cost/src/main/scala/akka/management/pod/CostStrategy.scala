/*
 * Copyright (C) 2017-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.management.pod

import akka.cluster.Member

import scala.collection.SortedSet

private[akka] trait CostStrategy {
  def costOf(member: Member, membersByAgeDesc: SortedSet[Member]): Option[Int]
}

object OlderCostsMore extends CostStrategy {
  def costOf(member: Member, membersByAgeDesc: SortedSet[Member]): Option[Int] = {
    val nrMembersToAnnotate = 2
    val maxCost = 10000
    val stepCost = 100

    membersByAgeDesc
      .take(nrMembersToAnnotate)
      .zipWithIndex
      .collectFirst {
        case (m, cost) if m.uniqueAddress == member.uniqueAddress => maxCost - (cost * stepCost)
      }
  }
}