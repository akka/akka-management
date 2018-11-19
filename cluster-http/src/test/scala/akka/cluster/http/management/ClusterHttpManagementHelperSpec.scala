/*
 * Copyright (C) 2017-2018 Lightbend Inc. <http://www.lightbend.com>
 */

package akka.cluster.http.management

import akka.actor.Address
import akka.cluster.MemberStatus._
import akka.cluster.{ Member, UniqueAddress }
import akka.management.cluster.ClusterHttpManagementHelper
import org.scalatest.{ Matchers, WordSpec }

class ClusterHttpManagementHelperSpec extends WordSpec with Matchers {

  "Oldest nodes per role" must {
    "work" in {
      val dc = "dc-one"
      val address1 = Address("akka", "Main", "hostname.com", 3311)
      val address2 = Address("akka", "Main", "hostname2.com", 3311)
      val address3 = Address("akka", "Main", "hostname3.com", 3311)
      val address4 = Address("akka", "Main", "hostname4.com", 3311)

      val uniqueAddress1 = UniqueAddress(address1, 1L)
      val uniqueAddress2 = UniqueAddress(address2, 2L)
      val uniqueAddress3 = UniqueAddress(address3, 3L)
      val uniqueAddress4 = UniqueAddress(address4, 4L)

      val clusterMember1 = new Member(uniqueAddress1, 1, Up, Set("one", "two", dc))
      val clusterMember2 = new Member(uniqueAddress2, 2, Joining, Set("one", "two", dc))
      val clusterMember3 = new Member(uniqueAddress3, 3, Joining, Set("three", dc))
      val clusterMember4 = new Member(uniqueAddress4, 4, Joining, Set(dc))

      val members = Seq(clusterMember1, clusterMember2, clusterMember3, clusterMember4)

      ClusterHttpManagementHelper.oldestPerRole(members) shouldEqual Map(
        "one" -> address1.toString,
        "two" -> address1.toString,
        "three" -> address3.toString,
        dc -> address1.toString
      )
    }
  }

}
