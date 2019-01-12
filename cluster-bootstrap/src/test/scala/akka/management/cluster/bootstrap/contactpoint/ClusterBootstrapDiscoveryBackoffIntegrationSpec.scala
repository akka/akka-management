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
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.RouteResult
import akka.management.cluster.bootstrap.ClusterBootstrap
import akka.stream.ActorMaterializer
import akka.testkit.{ SocketUtil, TestKit, TestProbe }
import com.typesafe.config.{ Config, ConfigFactory }
import org.scalactic.Tolerance
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{ Matchers, WordSpecLike }
import scala.concurrent.Future
import scala.concurrent.duration._

class ClusterBootstrapDiscoveryBackoffIntegrationSpec
    extends WordSpecLike
    with Matchers
    with Tolerance
    with ScalaFutures {

  "Cluster Bootstrap" should {

    var remotingPorts = Map.empty[String, Int]
    var contactPointPorts = Map.empty[String, Int]
    var unreachablePorts = Map.empty[String, Int]

    def config(id: String): Config = {
      val managementPort = SocketUtil.temporaryServerAddress("127.0.0.1").getPort
      val remotingPort = SocketUtil.temporaryServerAddress("127.0.0.1").getPort
      val unreachablePort = SocketUtil.temporaryServerAddress("127.0.0.1").getPort

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

          cluster.http.management.port = $managementPort
          remote.netty.tcp.port = $remotingPort

          management {

            cluster.bootstrap {
              contact-point-discovery {
                discovery-method = mock-dns

                service-namespace = "svc.cluster.local"

                stable-margin = 4 seconds

                interval = 500 ms
              }
            }
          }
        }
        """.stripMargin).withFallback(ConfigFactory.load())
    }

    val systemName = "backoff-discovery-system"
    val systemA = ActorSystem(systemName, config("A"))
    val systemB = ActorSystem(systemName, config("B"))

    val clusterA = Cluster(systemA)
    val clusterB = Cluster(systemB)

    val bootstrapA = ClusterBootstrap(systemA)
    val bootstrapB = ClusterBootstrap(systemB)

    val baseTime = System.currentTimeMillis()
    case class DiscoveryRequest(time: Long, attempt: Int, res: Future[_]) {
      override def toString = s"DiscoveryRequest(${(time - baseTime).millis}, $attempt, $res)"
    }
    val resolveProbe = TestProbe()(systemA)

    // prepare the "mock DNS" - this resolves to unreachable addresses for the first three
    // times it is called, thus testing that discovery is called multiple times and
    // that formation will eventually succeed once discovery returns reachable addresses

    var called = 0
    var call2Timestamp = 0L
    var call3Timestamp = 0L
    val name = s"$systemName.svc.cluster.local"

    MockDiscovery.set(Lookup(name).withProtocol("tcp").withPortName("management"), { () =>
      this.synchronized {
        called += 1

        if (called == 2)
          call2Timestamp = System.nanoTime()
        else if (called == 3)
          call3Timestamp = System.nanoTime()

        val res =
          if (called < 4)
            Future.failed(new Exception("Boom! Discovery failed, was rate limited for example..."))
          else
            Future.successful(Resolved(name,
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
                )))

        resolveProbe.ref ! DiscoveryRequest(System.currentTimeMillis(), called, res)
        res
      }
    })

    "start listening with the http contact-points on 2 systems" in {
      def start(system: ActorSystem, contactPointPort: Int) = {
        import system.dispatcher
        implicit val sys = system
        implicit val mat = ActorMaterializer()(system)

        val bootstrap: ClusterBootstrap = ClusterBootstrap(system)
        val routes = new HttpClusterBootstrapRoutes(bootstrap.settings).routes
        bootstrap.setSelfContactPoint(s"http://127.0.0.1:$contactPointPort")
        Http().bindAndHandle(RouteResult.route2HandlerFlow(routes), "127.0.0.1", contactPointPort)
      }

      start(systemA, contactPointPorts("A")).futureValue
      start(systemB, contactPointPorts("B")).futureValue
    }

    "poll discovery in exponentially increasing backoffs until eventually joining the returned nodes" in {
      bootstrapA.discovery.getClass should ===(classOf[MockDiscovery])

      bootstrapA.start()
      bootstrapB.start() // deliberately not discovering from this node, however its management port is available

      val pA = TestProbe()(systemA)
      clusterA.subscribe(pA.ref, classOf[MemberUp])

      pA.expectMsgType[CurrentClusterState]
      val up1 = pA.expectMsgType[MemberUp](45.seconds)
      info("" + up1)

      this.synchronized {
        called shouldBe >=(5)
        val durationBetweenCall2And3 = (call3Timestamp - call2Timestamp).nanos.toMillis
        info(s"duration between call 2 and 3 ${durationBetweenCall2And3} ms")
        durationBetweenCall2And3 shouldBe >=(
            (ClusterBootstrap(systemA).settings.contactPointDiscovery.interval * 2).toMillis)
      }
    }

    "terminate all systems" in {
      try TestKit.shutdownActorSystem(systemA, 3.seconds)
      finally TestKit.shutdownActorSystem(systemB, 3.seconds)
    }

  }

}
