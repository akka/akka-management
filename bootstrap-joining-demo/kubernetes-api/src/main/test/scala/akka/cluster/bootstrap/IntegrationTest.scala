/*
 * Copyright (C) 2017 Lightbend Inc. <http://www.lightbend.com>
 */

package akka.cluster.bootstrap

import java.util.UUID

import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpRequest
import akka.management.cluster.{ClusterHttpManagementJsonProtocol, ClusterMembers}
import akka.stream.{ActorMaterializer, ActorMaterializerSettings}
import akka.util.ByteString
import com.amazonaws.services.cloudformation.AmazonCloudFormationClientBuilder
import com.amazonaws.services.cloudformation.model._
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder
import com.amazonaws.services.ec2.model.{DescribeInstancesRequest, Filter, Instance, Reservation}
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.time.{Seconds, Span}
import org.scalatest.{BeforeAndAfterAll, FunSuite}
import spray.json._

import scala.concurrent.Future

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

  import collection.JavaConverters._

  private val log = Logging(system, classOf[IntegrationTest])

  private var clusterPublicIps: List[String] = List()

  private var clusterPrivateIps: List[String] = List()


  override def beforeAll(): Unit = {

    log.info("setting up infrastructure")

    ???

  }


  test("Integration Test for Kubernetes Integration") {

    implicit val patienceConfig: PatienceConfig = clusterBootstrapPatience
    val httpCallTimeout = Timeout(Span(3, Seconds))

    val expectedNodes: Set[String] = clusterPrivateIps.map(ip => s"akka.tcp://demo@$ip:2551").toSet

    eventually {

      log.info("querying the Cluster Http Management interface of each node, eventually we should see a well formed cluster")

      clusterPublicIps.foreach {
        nodeIp: String => {

          val result = httpGetRequest(s"http://$nodeIp:19999/cluster/members").futureValue(httpCallTimeout)
          assert(result._1 == 200)
          assert(result._2.nonEmpty)

          val clusterMembers = result._2.parseJson.convertTo[ClusterMembers]

          assert(clusterMembers.members.size == instanceCount)
          assert(clusterMembers.members.count(_.status == "Up") == instanceCount)
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


