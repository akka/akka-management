/*
 * Copyright (C) 2017-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.management.cluster.scaladsl

import akka.actor.ActorSystem
import akka.cluster.MemberStatus
import akka.testkit.TestKit
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

class ClusterMembershipCheckSpec
    extends TestKit(ActorSystem("ClusterHealthCheck"))
    with AnyWordSpecLike
    with Matchers
    with ScalaFutures
    with BeforeAndAfterAll {

  override def afterAll(): Unit = {
    shutdown(system)
  }

  "Cluster Health" should {
    "be unhealthy if current state not one of healthy states" in {
      val chc = new ClusterMembershipCheck(
        system,
        () => MemberStatus.joining,
        new ClusterMembershipCheckSettings(Set(MemberStatus.Up)))

      chc().futureValue shouldEqual false
    }
    "be unhealthy if current state is one of healthy states" in {
      val chc =
        new ClusterMembershipCheck(
          system,
          () => MemberStatus.Up,
          new ClusterMembershipCheckSettings(Set(MemberStatus.Up)))

      chc().futureValue shouldEqual true
    }
  }
}
