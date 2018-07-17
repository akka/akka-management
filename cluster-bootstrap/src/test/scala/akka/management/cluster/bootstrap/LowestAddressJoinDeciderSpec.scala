/*
 * Copyright (C) 2017 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.management.cluster.bootstrap

import java.time.LocalDateTime

import scala.concurrent.duration._

import akka.actor.ActorSystem
import akka.actor.Address
import akka.discovery.SimpleServiceDiscovery.ResolvedTarget
import akka.testkit.SocketUtil
import akka.testkit.TestKit
import com.typesafe.config.ConfigFactory
import org.scalatest.Matchers
import org.scalatest.WordSpecLike
import org.scalatest.concurrent.ScalaFutures

class LowestAddressJoinDeciderSpec extends WordSpecLike with Matchers with ScalaFutures {

  "LowestAddressJoinDecider" should {

    val managementPort = SocketUtil.temporaryServerAddress("127.0.0.1").getPort
    val remotingPort = SocketUtil.temporaryServerAddress("127.0.0.1").getPort

    val config =
      ConfigFactory.parseString(s"""
        akka {
          loglevel = INFO

          cluster.http.management.port = $managementPort
          remote.netty.tcp.port = $remotingPort

          mock-dns.class = "akka.discovery.MockDiscovery"

          management {
            cluster.bootstrap {
              contact-point-discovery {
                discovery-method = akka.mock-dns
                service-namespace = "svc.cluster.local"
                required-contact-point-nr = 3
              }
            }

            http {
              hostname = "127.0.0.1"
              base-path = "test"
              port = $managementPort
            }
          }
        }
        """)
    val system = ActorSystem("sys", config)
    val settings = ClusterBootstrapSettings(system.settings.config)

    val contactA = ResolvedTarget(host = "127.0.0.1", port = None, address = Some("127.0.0.1"))
    val contactB = ResolvedTarget(host = "b", port = None, address = None)
    val contactC = ResolvedTarget(host = "c", port = None, address = None)

    "sort ResolvedTarget by lowest hostname:port" in {
      List(ResolvedTarget("c", None, None), ResolvedTarget("a", None, None),
        ResolvedTarget("b", None, None)).sorted should ===(
        List(ResolvedTarget("a", None, None), ResolvedTarget("b", None, None), ResolvedTarget("c", None, None))
      )
      List(ResolvedTarget("c", Some(1), None), ResolvedTarget("a", Some(3), None),
        ResolvedTarget("b", Some(2), None)).sorted should ===(
        List(ResolvedTarget("a", Some(3), None), ResolvedTarget("b", Some(2), None),
          ResolvedTarget("c", Some(1), None))
      )
      List(ResolvedTarget("a", Some(2), None), ResolvedTarget("a", Some(1), None),
        ResolvedTarget("a", Some(3), None)).sorted should ===(
        List(ResolvedTarget("a", Some(1), None), ResolvedTarget("a", Some(2), None),
          ResolvedTarget("a", Some(3), None))
      )
    }

    "join existing cluster immediately" in {
      val decider = new LowestAddressJoinDecider(system, settings)
      val now = LocalDateTime.now()
      val info = new SeedNodesInformation(
        currentTime = now,
        contactPointsChangedAt = now.minusSeconds(2),
        contactPoints = Set(contactA, contactB, contactC),
        seedNodesObservations = Set(new SeedNodesObservation(now.minusSeconds(1), contactA,
            Address("akka", "sys", "127.0.0.1", 2552), Set(Address("akka", "sys", "127.0.0.1", 2552))))
      )
      decider.decide(info).futureValue should ===(JoinOtherSeedNodes(Set(Address("akka", "sys", "127.0.0.1", 2552))))
    }

    "keep probing when contact points changed within stable-margin" in {
      val decider = new LowestAddressJoinDecider(system, settings)
      val now = LocalDateTime.now()
      val info = new SeedNodesInformation(
        currentTime = now,
        contactPointsChangedAt = now.minusSeconds(2), // << 2 < stable-margin
        contactPoints = Set(contactA, contactB, contactC),
        seedNodesObservations = Set(new SeedNodesObservation(now.minusSeconds(1), contactA,
            Address("akka", "sys", "127.0.0.1", 2552), Set.empty),
          new SeedNodesObservation(now.minusSeconds(1), contactB, Address("akka", "sys", "b", 2552), Set.empty),
          new SeedNodesObservation(now.minusSeconds(1), contactC, Address("akka", "sys", "c", 2552), Set.empty))
      )
      decider.decide(info).futureValue should ===(KeepProbing)
    }

    "keep probing when not enough contact points" in {
      val decider = new LowestAddressJoinDecider(system, settings)
      val now = LocalDateTime.now()
      val info = new SeedNodesInformation(
        currentTime = now,
        contactPointsChangedAt = now.minusSeconds(2),
        contactPoints = Set(contactA, contactB), // << 2 < required-contact-point-nr
        seedNodesObservations = Set(new SeedNodesObservation(now.minusSeconds(1), contactA,
            Address("akka", "sys", "127.0.0.1", 2552), Set.empty),
          new SeedNodesObservation(now.minusSeconds(1), contactB, Address("akka", "sys", "b", 2552), Set.empty))
      )
      decider.decide(info).futureValue should ===(KeepProbing)
    }

    "keep probing when not enough confirmed contact points" in {
      val decider = new LowestAddressJoinDecider(system, settings)
      val now = LocalDateTime.now()
      val info = new SeedNodesInformation(
        currentTime = now,
        contactPointsChangedAt = now.minusSeconds(2),
        contactPoints = Set(contactA, contactB, contactC),
        seedNodesObservations = Set(new SeedNodesObservation(now.minusSeconds(1), contactA,
            Address("akka", "sys", "127.0.0.1", 2552), Set.empty),
          new SeedNodesObservation(now.minusSeconds(1), contactB, Address("akka", "sys", "b", 2552), Set.empty))
        // << 2 < required-contact-point-nr
      )
      decider.decide(info).futureValue should ===(KeepProbing)
    }

    "join self when all conditions met and self is lowest address" in {
      ClusterBootstrap(system).setSelfContactPoint(s"http://127.0.0.1:$managementPort/test")
      val decider = new LowestAddressJoinDecider(system, settings)
      val now = LocalDateTime.now()
      val info = new SeedNodesInformation(
        currentTime = now,
        contactPointsChangedAt = now.minusSeconds(6),
        contactPoints = Set(contactA, contactB, contactC),
        seedNodesObservations = Set(new SeedNodesObservation(now.minusSeconds(1), contactA,
            Address("akka", "sys", "127.0.0.1", 2552), Set.empty),
          new SeedNodesObservation(now.minusSeconds(1), contactB, Address("akka", "sys", "b", 2552), Set.empty),
          new SeedNodesObservation(now.minusSeconds(1), contactC, Address("akka", "sys", "c", 2552), Set.empty))
      )
      decider.decide(info).futureValue should ===(JoinSelf)
    }

    "terminate system" in {
      TestKit.shutdownActorSystem(system, 5.seconds)
    }

  }

}
