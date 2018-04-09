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
import com.amazonaws.services.cloudformation.AmazonCloudFormationClientBuilder
import com.amazonaws.services.cloudformation.model._
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder
import com.amazonaws.services.ec2.model.{DescribeInstancesRequest, Filter, Reservation}
import org.scalatest.concurrent.PatienceConfiguration.{Interval, Timeout}
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.time.{Seconds, Span, SpanSugar}
import org.scalatest.{BeforeAndAfterAll, FunSuite, Matchers}
import spray.json._

import scala.concurrent.{Await, Future}
import scala.language.postfixOps

trait HttpClient {

  implicit val system: ActorSystem = ActorSystem("simple")

  implicit val materializer: ActorMaterializer = ActorMaterializer(ActorMaterializerSettings(system))

  import system.dispatcher

  import scala.concurrent.duration._

  val http = Http(system)

  def httpGetRequest(url: String): Future[(Int, String)] = {
    http.singleRequest(HttpRequest(uri = url))
      .flatMap(r => r.entity.toStrict(3 seconds).map(s => r.status -> s))
      .flatMap(t => t._2.dataBytes.runFold(ByteString.empty)(_ ++ _).map(_.utf8String).map(_.filter(_ >= ' '))
        .map(r => t._1.intValue() -> r))
  }

}

class IntegrationTest
  extends FunSuite
    with Eventually
    with BeforeAndAfterAll
    with ScalaFutures
    with HttpClient
    with ClusterHttpManagementJsonProtocol
    with SpanSugar
    with Matchers {

  import collection.JavaConverters._

  private val buildId: String = System.getenv("BUILD_ID")
  assert(buildId != null, "BUILD_ID environment variable has to be defined")

  private val log = Logging(system, classOf[IntegrationTest])

  private val instanceCount = 3

  private val bucket = System.getenv("BUCKET") // bucket where zip file resulting from sbt universal:packageBin is stored

  private val region = "us-east-1"

  private val stackName = s"AkkaManagementIntegrationTestEC2TagBased-${buildId.replace(".", "-")}"

  private val awsCfClient = AmazonCloudFormationClientBuilder.standard().withRegion(region).build()

  private val awsEc2Client = AmazonEC2ClientBuilder.standard().withRegion(region).build()

  // Patience settings for the part where we wait for the CloudFormation script to complete
  private val createStackPatience: PatienceConfig =
    PatienceConfig(
      timeout = 15 minutes,
      interval = 10 seconds
    )

  // Patience settings for the actual cluster bootstrap part.
  // Once the CloudFormation stack has CREATE_COMPLETE status, the EC2 instances are
  // still "initializing" (seems to take a very long time) so we add some additional patience for that.
  private val clusterBootstrapPatience: PatienceConfig =
  PatienceConfig(
    timeout = 12 minutes,
    interval = 5 seconds
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
        new Parameter()
          .withParameterKey("Build")
          .withParameterValue(s"https://s3.amazonaws.com/$bucket/$buildId/app.zip"),
        new Parameter().withParameterKey("SSHLocation").withParameterValue(myIp),
        new Parameter().withParameterKey("InstanceCount").withParameterValue(instanceCount.toString),
        new Parameter().withParameterKey("InstanceType").withParameterValue("m3.xlarge"),
        new Parameter().withParameterKey("KeyPair").withParameterValue("none"),
        new Parameter().withParameterKey("Purpose").withParameterValue(s"demo-$buildId")
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

      conditions shouldBe true

    }

    if (conditions) {

      log.info("got CREATE_COMPLETE, trying to obtain IPs of EC2 instances launched")

      val asgName =
        dsr.getStacks.get(0).getOutputs.asScala.find(_.getOutputKey == "AutoScalingGroupName").get.getOutputValue

      val ips: List[(String, String)] = awsEc2Client
        .describeInstances(new DescribeInstancesRequest()
          .withFilters(new Filter("tag:aws:autoscaling:groupName", List(asgName).asJava)))
        .getReservations
        .asScala
        .flatMap((r: Reservation) =>
          r.getInstances.asScala.map(instance => (instance.getPublicIpAddress, instance.getPrivateIpAddress)))
        .toList
        .filter(ips =>
          ips._1 != null && ips._2 != null) // TODO: investigate whether there are edge cases that may makes this necessary

      clusterPublicIps = ips.map(_._1)
      clusterPrivateIps = ips.map(_._2)

      log.info("EC2 instances launched have the following public IPs {}", clusterPublicIps.mkString(", "))

    }

  }

  private def readTemplateFromResourceFolder(path: String): String = scala.io.Source.fromResource(path).mkString

  // we need this in order to tell AWS to allow the machine running the integration test to connect to the EC2 instances'
  // port 19999
  private def getMyIp: String = {
    val myIp: Future[(Int, String)] = httpGetRequest("http://checkip.amazonaws.com")
    val result = Await.result(myIp, atMost = 3 seconds)
    assert(result._1 == 200, "http://checkip.amazonaws.com did not return 200 OK")
    result._2
  }

  test("Integration Test for EC2 Tag Based Discovery") {

    implicit val patienceConfig: PatienceConfig = clusterBootstrapPatience
    val httpCallTimeout = Timeout(Span(3, Seconds))

    clusterPublicIps should have size instanceCount
    clusterPrivateIps should have size instanceCount

    val expectedNodes: Set[String] = clusterPrivateIps.map(ip => s"akka.tcp://demo@$ip:2551").toSet

    eventually {

      log.info("querying the Cluster Http Management interface of each node, eventually we should see a well formed cluster")

      clusterPublicIps.foreach { nodeIp: String => {

        val result = httpGetRequest(s"http://$nodeIp:19999/cluster/members").futureValue(httpCallTimeout)
        result._1 should ===(200)
        result._2 should not be 'empty

        val clusterMembers = result._2.parseJson.convertTo[ClusterMembers]

        clusterMembers.members should have size instanceCount
        clusterMembers.members.count(_.status == "Up") should ===(instanceCount)
        clusterMembers.members.map(_.node) should ===(expectedNodes)

        clusterMembers.unreachable should be('empty)
        clusterMembers.leader shouldBe defined
        clusterMembers.oldest shouldBe defined

      }
      }
    }
  }

  // this will remove every resource created by the integration test from the AWS account
  // this includes security rules, IAM roles, auto-scaling groups, EC2 instances etc.
  override def afterAll(): Unit = {
    log.info("tearing down infrastructure")
    eventually(timeout = Timeout(3 minutes), interval = Interval(3 seconds)) {
      // we put this into an an eventually block since we want to retry
      // for a while, in case it throws an exception.
      awsCfClient.deleteStack(new DeleteStackRequest().withStackName(stackName))
    }
    system.terminate()
  }


}
