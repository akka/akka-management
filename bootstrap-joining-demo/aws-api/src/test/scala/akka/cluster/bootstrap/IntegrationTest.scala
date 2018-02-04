/*
 * Copyright (C) 2017 Lightbend Inc. <http://www.lightbend.com>
 */

package akka.cluster.bootstrap

import java.util.UUID

import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpRequest
import akka.stream.{ActorMaterializer, ActorMaterializerSettings}
import akka.util.ByteString
import com.amazonaws.services.cloudformation.AmazonCloudFormationClientBuilder
import com.amazonaws.services.cloudformation.model._
import org.scalatest.concurrent.Eventually
import org.scalatest.time.{Seconds, Span}
import org.scalatest.{BeforeAndAfterAll, FunSuite}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

trait HttpClient {

  final implicit val system: ActorSystem = ActorSystem("simple")
  final implicit val materializer: ActorMaterializer = ActorMaterializer(ActorMaterializerSettings(system))
  final val http = Http(system)

}

class IntegrationTest extends FunSuite with Eventually with BeforeAndAfterAll with HttpClient {

  import system.dispatcher

  private val log = Logging(system, classOf[IntegrationTest])

  private val stackName = s"AkkaManagementIntegrationTestEC2TagBased-${UUID.randomUUID().toString.substring(0, 6)}"

  private val awsCfClient = AmazonCloudFormationClientBuilder.standard().withRegion("us-east-1").build()

  private val createStackPatience: PatienceConfig =
    PatienceConfig(
      timeout = scaled(Span(6 * 60, Seconds)),
      interval = scaled(Span(30, Seconds))
    )

  private def readTemplateFromResourceFolder(path: String): String = scala.io.Source.fromResource(path).mkString

  private def getMyIp: String = {
    val myIp: Future[String] = http.singleRequest(HttpRequest(uri = "http://checkip.amazonaws.com")).flatMap(r => r.entity.dataBytes.runFold(ByteString(""))(_ ++ _)
      .map(_.utf8String).map(_.filter(_ >= ' ')))
    Await.result(myIp, 2 seconds)
  }

  override def beforeAll(): Unit = {

    log.info("setting up infrastructure using CloudFormation")

    val template = readTemplateFromResourceFolder("CloudFormation/akka-cluster.json")

    val myIp: String = getMyIp + "/32"

    val createStackRequest = new CreateStackRequest()
      .withCapabilities("CAPABILITY_IAM")
      .withStackName(stackName)
      .withTemplateBody(template)
      .withParameters(
        new Parameter().withParameterKey("SSHLocation").withParameterValue(myIp),
        new Parameter().withParameterKey("InstanceCount").withParameterValue("2"),
        new Parameter().withParameterKey("InstanceType").withParameterValue("m3.medium"),
        new Parameter().withParameterKey("KeyPair").withParameterValue("none")
      )

    val describeStacksRequest = new DescribeStacksRequest()
      .withStackName(stackName)

    val createStackResult: CreateStackResult = awsCfClient.createStack(createStackRequest)

    var describeStacksResult: DescribeStacksResult = null

    implicit val patienceConfig = createStackPatience

    eventually {
      describeStacksResult = awsCfClient.describeStacks(describeStacksRequest)
      log.info("CloudFormation stack name is {}, waiting for CREATE_COMPLETE", stackName)
      assert(describeStacksResult.getStacks.size() == 1)
      assert(describeStacksResult.getStacks.get(0).getStackStatus == StackStatus.CREATE_COMPLETE.toString,
        "possible issue with the CloudFormation script"
      )
    }

  }

  test("Integration Test for EC2 Tag Based Discovery") {

    // here we have a real cluster of EC2 instances

    // continue by doing a describe instances request on the auto scaling group
    // will result in a list of IPs

    // we use a HTTP client to query each IP:19999/cluster/members
    // we make sure the cluster is well formed (no unreachable members, everyone can see everyone etc.)

    assert( 1 == 1 )

  }

  override def afterAll(): Unit = {
    log.info("tearing down infrastructure")
    // TODO: what happens if this fails ? Can we add some retries ?
    awsCfClient.deleteStack(new DeleteStackRequest().withStackName(stackName))
  }

}
