/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.cluster.bootstrap.contactpoint

import akka.actor.ActorSystem
import akka.cluster.Cluster
import akka.cluster.ClusterEvent.{ ClusterDomainEvent, CurrentClusterState, MemberUp }
import akka.cluster.bootstrap.{ ClusterBootstrap, ClusterBootstrapSettings }
import akka.cluster.http.management.ClusterHttpManagement
import akka.cluster.sharding.ShardCoordinator.Internal.DomainEvent
import akka.discovery.MockDiscovery
import akka.discovery.ServiceDiscovery.{ Resolved, ResolvedTarget }
import akka.testkit.{ SocketUtil, TestKit, TestProbe }
import com.typesafe.config.{ Config, ConfigFactory }
import org.scalatest.{ Matchers, WordSpecLike }

import scala.concurrent.duration._

class ClusterBootstrapDnsHttpIntegrationSpec extends WordSpecLike with Matchers {

  "Cluster Bootstrap" should {

    def config(): Config =
      ConfigFactory.parseString(s"""
        akka {
          loglevel = INFO

          cluster.jmx.multi-mbeans-in-same-jvm = on

          cluster.http.management.port = ${SocketUtil.temporaryServerAddress("127.0.0.1").getPort}
          remote.netty.tcp.port = ${SocketUtil.temporaryServerAddress("127.0.0.1").getPort}

          cluster.bootstrap {
            contact-point-discovery {
              discovery-method = akka.mock-dns

              service-namespace = "svc.cluster.local"
            }

            contact-point {
              no-seeds-stable-margin = 3 seconds
            }
          }
        }
        """.stripMargin).withFallback(ConfigFactory.load())

    val systemA = ActorSystem("System", config())
    val systemB = ActorSystem("System", config())
    val systemC = ActorSystem("System", config())

    val clusterA = Cluster(systemA)
    val clusterB = Cluster(systemB)
    val clusterC = Cluster(systemC)

    val bootstrapA = ClusterBootstrap(systemA)
    val bootstrapB = ClusterBootstrap(systemB)
    val bootstrapC = ClusterBootstrap(systemC)

    val settings = ClusterBootstrapSettings(systemA.settings.config)
    val httpBootstrap = new HttpClusterBootstrapRoutes(settings)

    // prepare the "mock DNS"
    val name = "system.svc.cluster.local"
    MockDiscovery.set(name,
      Resolved(name,
        List(
          ResolvedTarget(clusterA.selfAddress.host.get, clusterA.selfAddress.port),
          ResolvedTarget(clusterB.selfAddress.host.get, clusterB.selfAddress.port),
          ResolvedTarget(clusterC.selfAddress.host.get, clusterC.selfAddress.port)
        )))

    "start listening with the http contact-points on 3 systems" in {
      ClusterHttpManagement(Cluster(systemA)).start()
      ClusterHttpManagement(Cluster(systemB)).start()
      ClusterHttpManagement(Cluster(systemC)).start()
    }

    "join three DNS discovered nodes by forming new cluster (happy path)" in {
      bootstrapA.discovery.getClass should ===(classOf[MockDiscovery])

      bootstrapA.start()
      bootstrapB.start()
      bootstrapC.start()

      val pA = TestProbe()(systemA)
      clusterA.subscribe(pA.ref, classOf[ClusterDomainEvent])

      pA.expectMsgType[CurrentClusterState]
      val up1 = pA.expectMsgType[MemberUp](20.seconds)
      info("" + up1)
    }

    "terminate all systems" in {
      try TestKit.shutdownActorSystem(systemA, 3.seconds)
      finally try TestKit.shutdownActorSystem(systemB, 3.seconds)
      finally TestKit.shutdownActorSystem(systemC, 3.seconds)
    }

  }

}
