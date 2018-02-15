/*
 * Copyright (C) 2017 Lightbend Inc. <http://www.lightbend.com>
 */

package akka.cluster.bootstrap

import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpRequest
import akka.management.cluster.{ClusterHttpManagementJsonProtocol, ClusterMembers}
import akka.stream.{ActorMaterializer, ActorMaterializerSettings}
import akka.util.ByteString
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.time.{Seconds, Span}
import org.scalatest.{BeforeAndAfterAll, FunSuite}
import spray.json._

import scala.concurrent.Future

import io.kubernetes.client.ApiClient

trait HttpClient {

  implicit val system: ActorSystem = ActorSystem("simple")

  implicit val materializer: ActorMaterializer = ActorMaterializer(ActorMaterializerSettings(system))

  val http = Http(system)

  import system.dispatcher

  def httpGetRequest(url: String): Future[(Int, String)] = {
    http.singleRequest(HttpRequest(uri = url))
      .flatMap(r => r.entity.dataBytes.runFold(ByteString(""))(_ ++ _)
      .map(_.utf8String).map(_.filter(_ >= ' ')).map(body => (r.status.intValue(), body)))
  }

}

class IntegrationTest extends FunSuite with Eventually with BeforeAndAfterAll with ScalaFutures
  with HttpClient with ClusterHttpManagementJsonProtocol {

  private val log = Logging(system, classOf[IntegrationTest])

  private val expectedPodCount = 4

  private var clusterPublicIps: List[String] = List()

  private var clusterPrivateIps: List[String] = List()

  // Patience settings for the part where we wait for the Kubernetes to create the deployment
  private val createDeploymentPatience: PatienceConfig =
    PatienceConfig(
      timeout = scaled(Span(60, Seconds)),
      interval = scaled(Span(1, Seconds))
    )

  // Patience settings for the actual Akka part (once Kubernetes finishes setting up the deployment)
  private val clusterBootstrapPatience: PatienceConfig =
  PatienceConfig(
    timeout = scaled(Span(60, Seconds)),
    interval = scaled(Span(1, Seconds))
  )

  override def beforeAll(): Unit = {

    log.info("setting up infrastructure")

    val client = new ApiClient()

  }


  test("Integration Test for the Kubernetes Integration") {

    implicit val patienceConfig: PatienceConfig = clusterBootstrapPatience
    val httpCallTimeout = Timeout(Span(3, Seconds))

    val expectedNodes: Set[String] = clusterPrivateIps.map(ip => s"akka.tcp://Appka@$ip:2551").toSet

    eventually {

      log.info("querying the Cluster Http Management interface of each node, eventually we should see a well formed cluster")

      clusterPublicIps.foreach {
        nodeIp: String => {

          val result = httpGetRequest(s"http://$nodeIp:19999/cluster/members").futureValue(httpCallTimeout)
          assert(result._1 == 200)
          assert(result._2.nonEmpty)

          val clusterMembers = result._2.parseJson.convertTo[ClusterMembers]

          assert(clusterMembers.members.size == expectedPodCount)
          assert(clusterMembers.members.count(_.status == "Up") == expectedPodCount)
          assert(clusterMembers.members.map(_.node) == expectedNodes)

          assert(clusterMembers.unreachable.isEmpty)
          assert(clusterMembers.leader.isDefined)
          assert(clusterMembers.oldest.isDefined)

        }
      }
    }
  }

  override def afterAll(): Unit = {
    log.info("tearing down infrastructure")
    ???
    system.terminate()
  }

}


