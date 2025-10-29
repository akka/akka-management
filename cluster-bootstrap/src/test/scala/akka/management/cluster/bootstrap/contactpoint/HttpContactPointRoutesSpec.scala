/*
 * Copyright (C) 2017-2025 Lightbend Inc. <https://akka.io>
 */

package akka.management.cluster.bootstrap.contactpoint

import akka.cluster.{ Cluster, ClusterEvent }
import akka.event.NoLogging
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.management.cluster.bootstrap.ClusterBootstrapSettings
import akka.testkit.TestProbe
import org.scalatest.concurrent.Eventually
import org.scalatest.time.{ Millis, Seconds, Span }
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

class HttpContactPointRoutesSpec
    extends AnyWordSpecLike
    with Matchers
    with ScalatestRouteTest
    with HttpBootstrapJsonProtocol
    with Eventually {

  implicit override val patienceConfig: PatienceConfig =
    PatienceConfig(timeout = scaled(Span(3, Seconds)), interval = scaled(Span(50, Millis)))

  override def testConfigSource =
    s"""
    akka {
      remote {
        netty.tcp {
          hostname = "127.0.0.1"
          port = 0
        }
        artery.canonical {
          hostname = "127.0.0.1"
          port = 0
        }
      }
    }
    """.stripMargin

  "Http Bootstrap routes" should {

    val settings = ClusterBootstrapSettings(system.settings.config, NoLogging)
    val httpBootstrap = new HttpClusterBootstrapRoutes(settings)

    "empty list if node is not part of a cluster" in {
      ClusterBootstrapRequests.bootstrapSeedNodes("") ~> httpBootstrap.routes ~> check {
        responseAs[String] should include(""""seedNodes":[]""")
      }
    }

    "include seed nodes when part of a cluster" in {
      val cluster = Cluster(system)
      cluster.join(cluster.selfAddress)

      val p = TestProbe()
      cluster.subscribe(p.ref, ClusterEvent.InitialStateAsEvents, classOf[ClusterEvent.MemberUp])
      val up = p.expectMsgType[ClusterEvent.MemberUp]
      up.member should ===(cluster.selfMember)

      eventually {
        ClusterBootstrapRequests.bootstrapSeedNodes("") ~> httpBootstrap.routes ~> check {
          val response = responseAs[HttpBootstrapJsonProtocol.SeedNodes]
          response.seedNodes should !==(Set.empty)
          response.seedNodes.map(_.node) should contain(cluster.selfAddress)
        }
      }
    }
  }

}
