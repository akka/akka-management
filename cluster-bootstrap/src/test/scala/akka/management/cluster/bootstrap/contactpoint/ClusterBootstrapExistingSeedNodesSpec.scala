/*
 * Copyright (C) 2017-2018 Lightbend Inc. <http://www.lightbend.com>
 */

package akka.management.cluster.bootstrap.contactpoint

import akka.actor.{ ActorSystem, Address }
import akka.cluster.Cluster
import akka.discovery.MockDiscovery
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.RouteResult
import akka.management.cluster.bootstrap.ClusterBootstrap
import akka.stream.ActorMaterializer
import akka.testkit.{ SocketUtil, TestKit }
import com.typesafe.config.ConfigFactory
import org.scalatest.{ BeforeAndAfterAll, Matchers, WordSpecLike }

import scala.concurrent.duration._

class ClusterBootstrapExistingSeedNodesSpec(system: ActorSystem)
    extends TestKit(system)
    with WordSpecLike
    with Matchers
    with BeforeAndAfterAll {

  def this() {
    this(ActorSystem("ClusterBootstrapExistingSeedNodesSpec"))
  }

  val systemName = Logging.simpleName(classOf[ClusterBootstrapExistingSeedNodesSpec]) + "System"

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
      case JoinYourself ⇒ List(s"akka.tcp://${systemName}@127.0.0.1:${remotingPort}")
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
          discovery.mock-dns.class = "akka.discovery.MockDiscovery"

          management {

            cluster.bootstrap {
              contact-point-discovery {
                discovery-method = mock-dns

                service-namespace = "svc.cluster.local"

                stable-margin = 4 seconds
              }
            }
          }
        }
        """.stripMargin).withFallback(ConfigFactory.load())

    ActorSystem(systemName, config)
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

  "Cluster Bootstrap" should {

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

      awaitAssert {
        val members = clusterB.state.members
        members.size should ===(2)
        members.map(_.uniqueAddress.address.port) should ===(Set(clusterA.selfAddress.port, clusterB.selfAddress.port))
      }

      awaitAssert {
        val members = clusterNein.state.members
        members.size should ===(1)
        members.map(_.uniqueAddress.address.port) should ===(Set(clusterNein.selfAddress.port))
      }
    }

    "terminate all systems" in {
      try TestKit.shutdownActorSystem(systemA, 3.seconds)
      finally try TestKit.shutdownActorSystem(systemB, 3.seconds)
      finally TestKit.shutdownActorSystem(systemNein, 3.seconds)
    }

  }

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system, 5.seconds)
    TestKit.shutdownActorSystem(systemA, 5.seconds)
    TestKit.shutdownActorSystem(systemB, 5.seconds)
  }

}
