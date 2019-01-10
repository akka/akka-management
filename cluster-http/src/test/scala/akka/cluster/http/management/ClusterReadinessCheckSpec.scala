/*
 * Copyright (C) 2017-2018 Lightbend Inc. <http://www.lightbend.com>
 */

package akka.cluster.http.management

import akka.actor.{ActorSystem, ExtendedActorSystem}
import akka.cluster.MemberStatus
import akka.management.cluster.{
  ClusterReadinessCheck, ClusterReadinessCheckSettings}
import akka.testkit.TestKit
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{Matchers, WordSpecLike}

class ClusterReadinessCheckSpec extends TestKit(ActorSystem("ClusterHealthCheck")) with WordSpecLike with Matchers with ScalaFutures {

  val aes = system.asInstanceOf[ExtendedActorSystem]

  "Cluster Health" should {
    "be unhealthy if current state not one of healthy states" in {
      val chc = new ClusterReadinessCheck(aes, () => MemberStatus.joining,
        new ClusterReadinessCheckSettings(Set(MemberStatus.Up)))

      chc().futureValue shouldEqual false
    }
    "be unhealthy if current state is one of healthy states" in {
      val chc = new ClusterReadinessCheck(aes, () => MemberStatus.Up,
        new ClusterReadinessCheckSettings(Set(MemberStatus.Up)))

      chc().futureValue shouldEqual true
    }
  }
}
