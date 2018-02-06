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
import org.scalatest.concurrent.Eventually
import org.scalatest.time.{Seconds, Span}
import org.scalatest.{BeforeAndAfterAll, FunSuite}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

import spray.json._

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

class IntegrationTest extends FunSuite with Eventually with BeforeAndAfterAll with HttpClient with ClusterHttpManagementJsonProtocol {

  import system.dispatcher

  import collection.JavaConverters._

  private val log = Logging(system, classOf[IntegrationTest])

  private val bucket = System.getenv("BUCKET") // bucket where zip file resulting from sbt universal:packageBin is stored

  private val region = "us-east-1"

  private val stackName = s"AkkaManagementIntegrationTestEC2TagBased-${UUID.randomUUID().toString.substring(0, 6)}"

  private val awsCfClient = AmazonCloudFormationClientBuilder.standard().withRegion(region).build()

  private val awsEc2Client = AmazonEC2ClientBuilder.standard().withRegion(region).build()

  // Patience settings for the part where we wait for the CloudFormation script to complete
  private val createStackPatience: PatienceConfig =
    PatienceConfig(
      timeout = scaled(Span(6 * 60, Seconds)),
      interval = scaled(Span(30, Seconds))
    )

  // Patience settings for the actual Akka part
  private val clusterFormPatience: PatienceConfig =
    PatienceConfig(
      timeout = scaled(Span(4 * 60, Seconds)),
      interval = scaled(Span(4, Seconds))
    )


  private var clusterIps: List[String] = List()

  override def beforeAll(): Unit = {

    log.info("setting up infrastructure using CloudFormation")

    val template = readTemplateFromResourceFolder("CloudFormation/akka-cluster.json")

    val myIp: String = s"$getMyIp/32"

    val createStackRequest = new CreateStackRequest()
      .withCapabilities("CAPABILITY_IAM")
      .withStackName(stackName)
      .withTemplateBody(template)
      .withParameters(
        new Parameter().withParameterKey("Build").withParameterValue(s"https://s3.amazonaws.com/$bucket/app.zip"),
        new Parameter().withParameterKey("SSHLocation").withParameterValue(myIp),
        new Parameter().withParameterKey("InstanceCount").withParameterValue("2"),
        new Parameter().withParameterKey("InstanceType").withParameterValue("m3.xlarge"),
        new Parameter().withParameterKey("KeyPair").withParameterValue("s")
      )

    awsCfClient.createStack(createStackRequest)

    val describeStacksRequest = new DescribeStacksRequest().withStackName(stackName)

    var dsr: DescribeStacksResult = null

    def conditions: Boolean = (dsr.getStacks.size() == 1) && {
      val stack = dsr.getStacks.get(0)
      stack.getStackStatus == StackStatus.CREATE_COMPLETE.toString &&
        stack.getOutputs.size() >= 1 &&
        stack.getOutputs.asScala.exists(_.getOutputKey == "AutoScalingGroupName")
    }

    implicit val patienceConfig: PatienceConfig = createStackPatience

    eventually {

      log.info("CloudFormation stack name is {}, waiting for a CREATE_COMPLETE", stackName)

      dsr = awsCfClient.describeStacks(describeStacksRequest)

      assert(conditions)

    }

    if (conditions) {

      log.info("got CREATE_COMPLETE, trying to obtain IPs of EC2 instances launched")

      val asgName = dsr.getStacks.get(0).getOutputs.asScala.find(_.getOutputKey == "AutoScalingGroupName").get.getOutputValue

      clusterIps = awsEc2Client
        .describeInstances(new DescribeInstancesRequest().withFilters(new Filter("tag:aws:autoscaling:groupName", List(asgName).asJava)))
        .getReservations
        .asScala
        .flatMap((r: Reservation) => r.getInstances.asScala.map((instance: Instance) => instance.getPublicIpAddress))
        .toList
        .filter(_ != null) // TODO: investigate whether there are edge cases that may makes this necessary

      log.info("EC2 instances launched have the following public IPs {}", clusterIps.mkString(", "))

    }

  }

  private def readTemplateFromResourceFolder(path: String): String = scala.io.Source.fromResource(path).mkString

  private def getMyIp: String = {
    val myIp: Future[(Int, String)] = httpGetRequest("http://checkip.amazonaws.com")
    Await.result(myIp.map(_._2), atMost = 2 seconds)
  }

  test("Integration Test for EC2 Tag Based Discovery") {

    implicit val patienceConfig: PatienceConfig = clusterFormPatience
    eventually {
      log.info("querying the Cluster Http Management interface of each node, eventually we should see a well formed cluster")
      clusterIps.foreach {
        nodeIp => {
          // TODO: use ScalaTest syntax sugar for Futures
          val result = Await.result(httpGetRequest(s"http://$nodeIp:19999/cluster/members"), atMost = 3 seconds)
          assert(result._1 == 200)
          assert(result._2.nonEmpty)

          val clusterMembers = result._2.parseJson.convertTo[ClusterMembers]
          assert(clusterMembers.unreachable.isEmpty)
          assert(clusterMembers.members.size == 2)
          assert(clusterMembers.leader.isDefined)
          assert(clusterMembers.oldest.isDefined)

        }
      }
    }
  }

  override def afterAll(): Unit = {
    log.info("tearing down infrastructure")
    // TODO: what happens if this fails ? Can we add some retries ?
    awsCfClient.deleteStack(new DeleteStackRequest().withStackName(stackName))
    system.terminate()
  }

}
