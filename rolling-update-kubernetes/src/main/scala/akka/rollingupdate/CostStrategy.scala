/*
 * Copyright (C) 2017-2025 Lightbend Inc. <https://akka.io>
 */

package akka.rollingupdate

import akka.annotation.InternalApi
import akka.cluster.Member

import scala.collection.SortedSet

/**
 *  INTERNAL API
 *  Defines a trait for calculating the cost of removing a member from the akka cluster,
 *  given said member and the list of the members of the cluster from oldest to newest.
 */
@InternalApi private[rollingupdate] trait CostStrategy {
  def costOf(member: Member, membersByAgeDesc: SortedSet[Member]): Option[Int]
}

/**
 * INTERNAL API
 */
@InternalApi private[rollingupdate] object OlderCostsMore extends CostStrategy {
  def costOf(member: Member, membersByAgeDesc: SortedSet[Member]): Option[Int] = {
    val maxCost = 10000
    // avoiding using subsequent numbers: gives room for evolution and allows for manual interventions
    val stepCost = 100

    membersByAgeDesc.zipWithIndex.collectFirst {
      case (m, cost) if m.uniqueAddress == member.uniqueAddress => maxCost - (cost * stepCost)
    }
  }
}
