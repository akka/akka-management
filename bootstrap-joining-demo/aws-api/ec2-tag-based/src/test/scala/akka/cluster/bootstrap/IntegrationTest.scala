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

  private val buildId = UUID.randomUUID().toString.substring(0, 6)

  private val log = Logging(system, classOf[IntegrationTest])

  private val instanceCount = 3

  private val bucket = System.getenv("BUCKET") // bucket where zip file resulting from sbt universal:packageBin is stored

  private val region = "us-east-1"

  private val stackName = s"AkkaManagementIntegrationTestEC2TagBased-${buildId}"

  private val awsCfClient = AmazonCloudFormationClientBuilder.standard().withRegion(region).build()

  private val awsEc2Client = AmazonEC2ClientBuilder.standard().withRegion(region).build()

  // Patience settings for the part where we wait for the CloudFormation script to complete
  private val createStackPatience: PatienceConfig =
    PatienceConfig(
      timeout = scaled(Span(12 * 60, Seconds)),
      interval = scaled(Span(3, Seconds))
    )

  // Patience settings. Once the CloudFormation stack has CREATE_COMPLETE status, the EC2 instances are
  // still "initializing" (seems to take a long time)
  private val clusterBootstrapPatience: PatienceConfig =
    PatienceConfig(
      timeout = scaled(Span(8 * 60, Seconds)),
      interval = scaled(Span(2, Seconds))
    )

  private var clusterPublicIps: List[String] = List()

  private var clusterPrivateIps: List[String] = List()

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
        new Parameter().withParameterKey("InstanceCount").withParameterValue(instanceCount.toString),
        new Parameter().withParameterKey("InstanceType").withParameterValue("m3.xlarge"),
        new Parameter().withParameterKey("KeyPair").withParameterValue("none"),
        new Parameter().withParameterKey("Purpose")
          .withParameterValue("demo" + "-" + buildId + Option(System.getenv("TRAVIS_SCALA_VERSION")).map(v => "-" + v).getOrElse(""))
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

      val ips: List[(String, String)] = awsEc2Client
        .describeInstances(new DescribeInstancesRequest().withFilters(new Filter("tag:aws:autoscaling:groupName", List(asgName).asJava)))
        .getReservations
        .asScala
        .flatMap((r: Reservation) => r.getInstances.asScala.map((instance: Instance) => (instance.getPublicIpAddress, instance.getPrivateIpAddress)))
        .toList
        .filter(ips => ips._1 !=null && ips._2 !=null) // TODO: investigate whether there are edge cases that may makes this necessary

      clusterPublicIps = ips.map(_._1)
      clusterPrivateIps = ips.map(_._2)

      log.info("EC2 instances launched have the following public IPs {}", clusterPublicIps.mkString(", "))

    }

  }

  private def readTemplateFromResourceFolder(path: String): String = scala.io.Source.fromResource(path).mkString

  private def getMyIp: String = {
    val myIp: Future[(Int, String)] = httpGetRequest("http://checkip.amazonaws.com")
    val httpCallTimeout = Timeout(Span(3, Seconds))
    myIp.futureValue(httpCallTimeout)._2
  }

  test("Integration Test for EC2 Tag Based Discovery") {

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
    // TODO: what happens if this fails ? Can we add some retries ?
    awsCfClient.deleteStack(new DeleteStackRequest().withStackName(stackName))
    system.terminate()
  }

}
