/*
 * Copyright (C) 2017-2018 Lightbend Inc. <http://www.lightbend.com>
 */

package akka.management.cluster.bootstrap.contactpoint

import java.net.InetAddress

import akka.actor.ActorSystem
import akka.cluster.Cluster
import akka.cluster.ClusterEvent.{ CurrentClusterState, MemberUp }
import akka.discovery.ServiceDiscovery.{ Resolved, ResolvedTarget }
import akka.discovery.{ Lookup, MockDiscovery }
import akka.management.AkkaManagement
import akka.management.cluster.bootstrap.ClusterBootstrap
import akka.testkit.{ SocketUtil, TestKit, TestProbe }
import com.typesafe.config.ConfigFactory
import org.scalatest.{ Matchers, WordSpecLike }
import scala.concurrent.Future
import scala.concurrent.duration._

/**
 * This test ensures that the client and server both respect the base-path setting and thus that the boostrapping
 * process works correctly when this setting is specified.
 */
class ClusterBootstrapBasePathIntegrationSpec extends WordSpecLike with Matchers {

  "Cluster Bootstrap" should {
    val managementPort = SocketUtil.temporaryServerAddress("127.0.0.1").getPort
    val remotingPort = SocketUtil.temporaryServerAddress("127.0.0.1").getPort

    val config =
      ConfigFactory.parseString(s"""
        akka {
          loglevel = INFO

          cluster.jmx.multi-mbeans-in-same-jvm = on

          cluster.http.management.port = $managementPort
          remote.netty.tcp.port = $remotingPort

          discovery.mock-dns.class = "akka.discovery.MockDiscovery"

          management {
            cluster.bootstrap {
              contact-point-discovery {
                discovery-method = mock-dns
                service-namespace = "svc.cluster.local"
                required-contact-point-nr = 1
              }
            }

            http {
              hostname = "127.0.0.1"
              base-path = "test"
              port = $managementPort
            }
          }
        }
        """.stripMargin).withFallback(ConfigFactory.load())

    val systemA = ActorSystem("basepathsystem", config)

    val clusterA = Cluster(systemA)

    val managementA = AkkaManagement(systemA)

    val bootstrapA = ClusterBootstrap(systemA)

    // prepare the "mock DNS"
    val name = "basepathsystem.svc.cluster.local"
    MockDiscovery.set(Lookup(name).withProtocol("tcp").withPortName("management"),
      () =>
        Future.successful(
          Resolved(name,
            List(
              ResolvedTarget(host = "127.0.0.1", port = Some(managementPort),
                address = Option(InetAddress.getByName("127.0.0.1")))
            ))
      ))

    "start listening with the http contact-points on system" in {
      managementA.start()

      bootstrapA.start()
    }

    "join self, thus forming new cluster (happy path)" in {
      bootstrapA.discovery.getClass should ===(classOf[MockDiscovery])

      bootstrapA.start()

      val pA = TestProbe()(systemA)
      clusterA.subscribe(pA.ref, classOf[MemberUp])

      pA.expectMsgType[CurrentClusterState]
      val up1 = pA.expectMsgType[MemberUp](30.seconds)
      info("" + up1)
    }

    "terminate system" in {
      TestKit.shutdownActorSystem(systemA, 3.seconds)
    }

  }

}
