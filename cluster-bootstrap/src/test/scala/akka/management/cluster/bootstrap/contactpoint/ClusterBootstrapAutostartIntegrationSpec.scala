/*
 * Copyright (C) 2017-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.management.cluster.bootstrap.contactpoint

import akka.actor.ActorSystem
import akka.cluster.Cluster
import akka.cluster.MemberStatus
import akka.discovery.Lookup
import akka.discovery.MockDiscovery
import akka.discovery.ServiceDiscovery.Resolved
import akka.discovery.ServiceDiscovery.ResolvedTarget
import akka.testkit.SocketUtil
import akka.testkit.TestKit
import akka.testkit.TestProbe
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import java.net.InetAddress
import scala.concurrent.Future
import scala.concurrent.duration._

class ClusterBootstrapAutostartIntegrationSpec extends AnyWordSpecLike with Matchers with ScalaFutures {
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
          # trigger autostart by loading the extension through config
          extensions = ["akka.management.cluster.bootstrap.ClusterBootstrap"]

          cluster.jmx.multi-mbeans-in-same-jvm = on

          # this can be referred to in tests to use the mock discovery implementation
          discovery.mock-dns.class = "akka.discovery.MockDiscovery"

          remote.netty.tcp.port = $remotingPort
          remote.artery.canonical.port = $remotingPort
          remote.artery.canonical.hostname = "127.0.0.1"

          management {
            http.port = $managementPort
            http.hostname = "127.0.0.1"
            cluster.bootstrap {
              contact-point-discovery {
                discovery-method = mock-dns

                service-name = "service"
                port-name = "management-auto"
                protocol = "tcp2"

                service-namespace = "svc.cluster.local"

                stable-margin = 4 seconds
              }
            }
          }
        }
        """.stripMargin).withFallback(ConfigFactory.load()).resolve()
    }

    // allocate all ports in one go to avoid clashes
    val ports = SocketUtil.temporaryServerAddresses(6, "127.0.0.1").map(_.getPort)

    remotingPorts += "A" -> ports(0)
    remotingPorts += "B" -> ports(1)
    remotingPorts += "C" -> ports(2)

    contactPointPorts += "A" -> ports(3)
    contactPointPorts += "B" -> ports(4)
    contactPointPorts += "C" -> ports(5)

    val systemA = ActorSystem("System", config("A"))
    val systemB = ActorSystem("System", config("B"))
    val systemC = ActorSystem("System", config("C"))

    val clusterA = Cluster(systemA)
    val clusterB = Cluster(systemB)
    val clusterC = Cluster(systemC)

    // prepare the "mock DNS"
    val name = "service.svc.cluster.local"
    MockDiscovery.set(
      Lookup(name, Some("management-auto"), Some("tcp2")),
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

    "join three DNS discovered nodes by forming new cluster (happy path)" in {
      val pA = TestProbe()(systemA)
      pA.awaitAssert({
        clusterA.state.members should have size(3)
        clusterA.state.members.count(_.status == MemberStatus.Up) should === (3)
      }, 20.seconds)
    }

    "terminate a system when autostart fails" in {
      // failing because we re-use the same port for management here (but not for remoting
      // as that itself terminates the system on start)
      val systemA = ActorSystem("System", ConfigFactory.parseString("""
        akka.remote.netty.tcp.port = 0
        akka.remote.artery.canonical.port = 0
      """).withFallback(config("A")))
      systemA.whenTerminated.futureValue
    }

    "terminate all systems" in {
      try TestKit.shutdownActorSystem(systemA, 3.seconds)
      finally try TestKit.shutdownActorSystem(systemB, 3.seconds)
      finally TestKit.shutdownActorSystem(systemC, 3.seconds)
    }

  }

}
