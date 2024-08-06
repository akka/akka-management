/*
 * Copyright (C) 2017-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.management.cluster

import scala.collection.immutable

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ HttpRequest, StatusCodes }
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.management.scaladsl.ManagementRouteProviderSettings
import akka.testkit.SocketUtil
import com.typesafe.config.ConfigFactory
import org.scalatest.concurrent.{ Eventually, ScalaFutures }
import org.scalatest.time.{ Millis, Seconds, Span }
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class MultiDcSpec
    extends AnyWordSpec
    with Matchers
    with ScalaFutures
    with ClusterHttpManagementJsonProtocol
    with Eventually {

  implicit val patience: PatienceConfig = PatienceConfig(timeout = Span(10, Seconds), interval = Span(50, Millis))

  val config = ConfigFactory.parseString(
    """
      |akka.actor.provider = "cluster"
      |akka.remote.log-remote-lifecycle-events = off
      |akka.remote.artery.canonical.hostname = "127.0.0.1"
      |akka.remote.artery.enabled = true
      |#akka.loglevel = DEBUG
    """.stripMargin
  )

  "Http cluster management" must {
    "allow multiple DCs" in {
      val immutable.IndexedSeq(httpPortA: Int, portA: Int, portB) =
        SocketUtil.temporaryServerAddresses(3, "127.0.0.1").map(_.getPort)

      val dcA = ConfigFactory.parseString(
        s"""
           |akka.management.http.hostname = "127.0.0.1"
           |akka.management.http.port = $httpPortA
           |akka.cluster.seed-nodes = ["akka://MultiDcSystem@127.0.0.1:$portA"]
           |akka.cluster.multi-data-center.self-data-center = "DC-A"
           |akka.remote.artery.canonical.port = $portA
           |akka.remote.artery.canonical.port = $portA
           |          """.stripMargin
      )
      val dcB = ConfigFactory.parseString(
        s"""
           |akka.cluster.seed-nodes = ["akka://MultiDcSystem@127.0.0.1:$portA"]
           |akka.cluster.multi-data-center.self-data-center = "DC-B"
           |akka.remote.artery.canonical.port = $portB
           |          """.stripMargin
      )

      implicit val dcASystem = ActorSystem("MultiDcSystem", config.withFallback(dcA))
      val dcBSystem = ActorSystem("MultiDcSystem", config.withFallback(dcB))

      val routeSettings =
        ManagementRouteProviderSettings(selfBaseUri = s"http://127.0.0.1:$httpPortA", readOnly = false)

      try {
        Http()
          .newServerAt("127.0.0.1", httpPortA)
          .bind(ClusterHttpManagementRouteProvider(dcASystem).routes(routeSettings))
          .futureValue

        eventually {
          val response =
            Http().singleRequest(HttpRequest(uri = s"http://127.0.0.1:$httpPortA/cluster/members")).futureValue
          response.status should equal(StatusCodes.OK)
          val members = Unmarshal(response.entity).to[ClusterMembers].futureValue
          members.members.size should equal(2)
          members.members.map(_.status) should equal(Set("Up"))
        }
      } finally {
        dcASystem.terminate()
        dcBSystem.terminate()
      }
    }
  }
}
