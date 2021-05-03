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
import akka.discovery.ServiceDiscovery.Resolved
import akka.discovery.ServiceDiscovery.ResolvedTarget
import akka.discovery.Lookup
import akka.discovery.MockDiscovery
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.RouteResult
import akka.management.cluster.bootstrap.ClusterBootstrap
import akka.testkit.SocketUtil
import akka.testkit.TestKit
import akka.testkit.TestProbe
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

class ClusterBootstrapRetryUnreachableContactPointIntegrationSpec extends AnyWordSpecLike with Matchers {

  "Cluster Bootstrap" should {

    var remotingPorts = Map.empty[String, Int]
    var contactPointPorts = Map.empty[String, Int]
    var unreachablePorts = Map.empty[String, Int]

    def config(id: String): Config = {
      val Vector(managementPort, remotingPort, unreachablePort) =
        SocketUtil.temporaryServerAddresses(3, "127.0.0.1").map(_.getPort)

      info(s"System [$id]:  management port: $managementPort")
      info(s"System [$id]:    remoting port: $remotingPort")
      info(s"System [$id]: unreachable port: $unreachablePort")

      contactPointPorts = contactPointPorts.updated(id, managementPort)
      remotingPorts = remotingPorts.updated(id, remotingPort)
      unreachablePorts = unreachablePorts.updated(id, unreachablePort)

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

                service-namespace = "svc.cluster.local"

                stable-margin = 4 seconds

                port-name = "management"
              }
            }
          }
        }
        """.stripMargin).withFallback(ConfigFactory.load())
    }

    val systemA = ActorSystem("SystemUnreachableNodes", config("A"))
    val systemB = ActorSystem("SystemUnreachableNodes", config("B"))

    val clusterA = Cluster(systemA)
    val clusterB = Cluster(systemB)

    val bootstrapA = ClusterBootstrap(systemA)
    val bootstrapB = ClusterBootstrap(systemB)

    // prepare the "mock DNS" - this resolves to unreachable addresses for the first three
    // times it is called, thus testing that discovery is called multiple times and
    // that formation will eventually succeed once discovery returns reachable addresses

    var called = 0

    val name = "systemunreachablenodes.svc.cluster.local"

    MockDiscovery.set(
      Lookup(name).withProtocol("tcp").withPortName("management"), { () =>
        called += 1

        Future.successful(
          if (called > 3)
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
                )
              )
            )
          else
            Resolved(
              name,
              List(
                ResolvedTarget(
                  host = clusterA.selfAddress.host.get,
                  port = unreachablePorts.get("A"),
                  address = Option(InetAddress.getByName(clusterA.selfAddress.host.get))
                ),
                ResolvedTarget(
                  host = clusterB.selfAddress.host.get,
                  port = unreachablePorts.get("B"),
                  address = Option(InetAddress.getByName(clusterB.selfAddress.host.get))
                )
              )
            )
        )
      }
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
    }

    "eventually join three discovered nodes by forming new cluster" in {
      bootstrapA.discovery.getClass should ===(classOf[MockDiscovery])

      bootstrapA.start()
      bootstrapB.start()

      val pA = TestProbe()(systemA)
      clusterA.subscribe(pA.ref, classOf[MemberUp])

      pA.expectMsgType[CurrentClusterState]
      val up1 = pA.expectMsgType[MemberUp](45.seconds)
      info("" + up1)

      called >= 3 shouldBe true
    }

    "terminate all systems" in {
      try TestKit.shutdownActorSystem(systemA, 3.seconds)
      finally TestKit.shutdownActorSystem(systemB, 3.seconds)
    }

  }

}
