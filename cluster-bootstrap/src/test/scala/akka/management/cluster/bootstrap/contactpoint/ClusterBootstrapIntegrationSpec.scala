/*
 * Copyright (C) 2017-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.management.cluster.bootstrap.contactpoint

import java.net.InetAddress

import scala.concurrent.Future
import scala.concurrent.duration._

import akka.actor.ActorSystem
import akka.cluster.Cluster
import akka.cluster.ClusterEvent.CurrentClusterState
import akka.cluster.ClusterEvent.MemberUp
import akka.discovery.Lookup
import akka.discovery.MockDiscovery
import akka.discovery.ServiceDiscovery.Resolved
import akka.discovery.ServiceDiscovery.ResolvedTarget
import akka.http.scaladsl.Http
import akka.management.cluster.bootstrap.ClusterBootstrap
import akka.testkit.SocketUtil
import akka.testkit.TestKit
import akka.testkit.TestProbe
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

class ClusterBootstrapIntegrationSpec extends AnyWordSpecLike with Matchers {

  "Cluster Bootstrap" should {

    var remotingPorts = Map.empty[String, Int]
    var contactPointPorts = Map.empty[String, Int]

    def config(id: String): Config = {
      val managementPort = contactPointPorts(id)
      val remotingPort = remotingPorts(id)
      info(s"System [$id]: management port: $managementPort")
      info(s"System [$id]:   remoting port: $remotingPort")

      contactPointPorts = contactPointPorts.updated(id, managementPort)
      remotingPorts = remotingPorts.updated(id, remotingPort)

      ConfigFactory.parseString(s"""
        akka {
          loglevel = INFO

          cluster.jmx.multi-mbeans-in-same-jvm = on

          # this can be referred to in tests to use the mock discovery implementation
          discovery.mock-dns.class = "akka.discovery.MockDiscovery"

          remote.netty.tcp.port = $remotingPort
          remote.artery.canonical.port = $remotingPort
          remote.artery.canonical.hostname = "127.0.0.1"

          management {
            http.management.port = $managementPort
            http.management.hostname = "127.0.0.1"
            cluster.bootstrap {
              contact-point-discovery {
                discovery-method = mock-dns

                service-name = "service"
                port-name = "management2"
                protocol = "tcp2"

                service-namespace = "svc.cluster.local"

                stable-margin = 4 seconds
              }
            }
          }
        }
        """.stripMargin).withFallback(ConfigFactory.load())
    }

    // allocate all ports in one go to avoid clashes
    val ports = SocketUtil.temporaryServerAddresses(6, "127.0.0.1").map(_.getPort)

    remotingPorts += "A" -> ports(0)
    remotingPorts += "B" -> ports(1)
    remotingPorts += "C" -> ports(2)

    contactPointPorts += "A" -> ports(3)
    contactPointPorts += "B" -> ports(4)
    contactPointPorts += "C" -> ports(5)

    val systemA = ActorSystem("ClusterBootstrapIntegrationSpec", config("A"))
    val systemB = ActorSystem("ClusterBootstrapIntegrationSpec", config("B"))
    val systemC = ActorSystem("ClusterBootstrapIntegrationSpec", config("C"))

    val clusterA = Cluster(systemA)
    val clusterB = Cluster(systemB)
    val clusterC = Cluster(systemC)

    val bootstrapA = ClusterBootstrap(systemA)
    val bootstrapB = ClusterBootstrap(systemB)
    val bootstrapC = ClusterBootstrap(systemC)

    // prepare the "mock DNS"
    val name = "service.svc.cluster.local"
    MockDiscovery.set(
      Lookup(name, Some("management2"), Some("tcp2")),
      () =>
        Future.successful(
          Resolved(
            name,
            List(
              ResolvedTarget(
                host = clusterA.selfAddress.host.get,
                port = contactPointPorts.get("A"),
                address = Option(InetAddress.getByName(clusterA.selfAddress.host.get))
              ),
              ResolvedTarget(
                host = clusterB.selfAddress.host.get,
                port = contactPointPorts.get("B"),
                address = Option(InetAddress.getByName(clusterB.selfAddress.host.get))
              ),
              ResolvedTarget(
                host = clusterC.selfAddress.host.get,
                port = contactPointPorts.get("C"),
                address = Option(InetAddress.getByName(clusterC.selfAddress.host.get))
              )
            )
          )
        )
    )

    "start listening with the http contact-points on 3 systems" in {
      def start(system: ActorSystem, contactPointPort: Int) = {
        implicit val sys = system

        val bootstrap = ClusterBootstrap(system)
        val routes = new HttpClusterBootstrapRoutes(bootstrap.settings).routes
        bootstrap.setSelfContactPoint(s"http://127.0.0.1:$contactPointPort")
        Http().newServerAt("127.0.0.1", contactPointPort).bind(routes)
      }

      start(systemA, contactPointPorts("A"))
      start(systemB, contactPointPorts("B"))
      start(systemC, contactPointPorts("C"))
    }

    "join three DNS discovered nodes by forming new cluster (happy path)" in {
      bootstrapA.discovery.getClass should ===(classOf[MockDiscovery])

      val pA = TestProbe()(systemA)
      clusterA.subscribe(pA.ref, classOf[MemberUp])

      bootstrapA.start()
      bootstrapB.start()
      bootstrapC.start()

      try {
        pA.expectMsgType[CurrentClusterState]
        val up1 = pA.expectMsgType[MemberUp](30.seconds)
        info("" + up1)
      } catch {
        case t: AssertionError =>
          println("Member up not ")
          throw t
      }
    }

    "terminate all systems" in {
      try TestKit.shutdownActorSystem(systemA, 3.seconds)
      finally try TestKit.shutdownActorSystem(systemB, 3.seconds)
      finally TestKit.shutdownActorSystem(systemC, 3.seconds)
    }

  }

}
