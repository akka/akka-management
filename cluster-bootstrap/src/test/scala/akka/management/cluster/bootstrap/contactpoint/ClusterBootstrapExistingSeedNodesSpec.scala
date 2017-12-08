/*
 * Copyright (C) 2017 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.management.cluster.bootstrap.contactpoint

/*
 * Copyright (C) 2017 Lightbend Inc. <http://www.lightbend.com>
 */

import akka.actor.{ ActorSystem, Address }
import akka.cluster.Cluster
import akka.cluster.ClusterEvent.{ ClusterDomainEvent, CurrentClusterState, MemberUp }
import akka.discovery.MockDiscovery
import akka.discovery.SimpleServiceDiscovery.{ Resolved, ResolvedTarget }
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.RouteResult
import akka.management.cluster.bootstrap.ClusterBootstrap
import akka.stream.ActorMaterializer
import akka.testkit.{ SocketUtil, TestKit, TestProbe }
import com.typesafe.config.{ Config, ConfigFactory }
import org.scalatest.{ Matchers, WordSpecLike }

import scala.concurrent.duration._

class ClusterBootstrapExistingSeedNodesSpec extends WordSpecLike with Matchers {

  "Cluster Bootstrap" should {

    var remotingPorts = Map.empty[String, Int]
    var contactPointPorts = Map.empty[String, Int]

    // sneaky marker object:
    val JoinYourself: List[Address] = List(null, null, null)

    def newSystem(id: String, seedNodes: List[Address]) = {
      val managementPort = SocketUtil.temporaryServerAddress("127.0.0.1").getPort
      val remotingPort = SocketUtil.temporaryServerAddress("127.0.0.1").getPort

      info(s"System [$id]:   remoting port: $remotingPort")

      contactPointPorts = contactPointPorts.updated(id, managementPort)
      remotingPorts = remotingPorts.updated(id, remotingPort)

      val seeds = (seedNodes match {
        case JoinYourself ⇒ List(s"akka.tcp://System@127.0.0.1:${remotingPort}")
        case _ ⇒ seedNodes.map(_.toString)
      }).mkString("""["""", """", """", """"] """)

      val config = ConfigFactory.parseString(s"""
        akka {
          loglevel = INFO

          cluster.jmx.multi-mbeans-in-same-jvm = on

          cluster.seed-nodes = ${seeds}

          cluster.http.management.port = $managementPort
          remote.netty.tcp.port = $remotingPort

          # this can be referred to in tests to use the mock discovery implementation
          mock-dns.impl = "akka.discovery.MockDiscovery"

          management {

            cluster.bootstrap {
              contact-point-discovery {
                discovery-method = akka.mock-dns

                service-namespace = "svc.cluster.local"
              }

              contact-point {
                no-seeds-stable-margin = 4 seconds
              }
            }
          }
        }
        """.stripMargin).withFallback(ConfigFactory.load())

      ActorSystem("System", config)
    }

    val systemA = newSystem("A", seedNodes = JoinYourself)
    val clusterA = Cluster(systemA)

    // system B is expected to join system A, since seed-node conf trumps the bootstrap mechanism
    val systemB = newSystem("B", seedNodes = List(clusterA.selfAddress))
    val clusterB = Cluster(systemB)

    val systemNein = newSystem("Nein", seedNodes = JoinYourself)
    val clusterNein = Cluster(systemNein)

    val bootstrapA = ClusterBootstrap(systemA)
    val bootstrapB = ClusterBootstrap(systemB)
    val bootstrapNein = ClusterBootstrap(systemNein)

    val name = "system.svc.cluster.local"
    MockDiscovery.set(name,
      Resolved(name,
        List(
          // we yield the Nein system here, because we want to see that the configured seed-node is joined, and NOT the discovered one
          ResolvedTarget(clusterB.selfAddress.host.get, contactPointPorts.get("Nein"))
        )))

    "start listening with the http contact-points on all systems" in {
      def start(system: ActorSystem, contactPointPort: Int) = {
        import system.dispatcher
        implicit val sys = system
        implicit val mat = ActorMaterializer()(system)

        val bootstrap = ClusterBootstrap(system)
        val routes = new HttpClusterBootstrapRoutes(bootstrap.settings).routes
        bootstrap.setSelfContactPoint(s"http://127.0.0.1:$contactPointPort")
        Http().bindAndHandle(RouteResult.route2HandlerFlow(routes), "127.0.0.1", contactPointPort)
      }

      start(systemA, contactPointPorts("A"))
      start(systemB, contactPointPorts("B"))
      start(systemNein, contactPointPorts("Nein"))
    }

    "since the nodes have seed nodes configured in config, they should join that, rather than perform bootstrap" in {
      bootstrapA.discovery.getClass should ===(classOf[MockDiscovery])

      bootstrapA.start()
      bootstrapB.start()
      bootstrapNein.start()

      val pB = TestProbe()(systemA)
      pB.awaitAssert {
        clusterB.subscribe(pB.ref, classOf[MemberUp])
        val stateB = pB.expectMsgType[CurrentClusterState]
        stateB.members.size should ===(2)
        stateB.members.map(_.uniqueAddress.address.port) should ===(Set(clusterA.selfAddress.port,
            clusterB.selfAddress.port))
      }

      val pNein = TestProbe()(systemA)
      pNein.awaitAssert {
        clusterNein.subscribe(pNein.ref, classOf[MemberUp])
        val stateNein = pNein.expectMsgType[CurrentClusterState]
        stateNein.members.size should ===(1)
        stateNein.members.map(_.uniqueAddress.address.port) should ===(Set(clusterNein.selfAddress.port))
      }
    }

    "terminate all systems" in {
      try TestKit.shutdownActorSystem(systemA, 3.seconds)
      finally try TestKit.shutdownActorSystem(systemB, 3.seconds)
    }

  }

}
