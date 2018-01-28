/*
 * Copyright (C) 2017 Lightbend Inc. <http://www.lightbend.com>
 */

package akka.cluster.bootstrap

import com.amazonaws.services.cloudformation.AmazonCloudFormationClientBuilder
import com.amazonaws.services.cloudformation.model._
import org.scalatest.FunSuite
import org.scalatest.concurrent.{Eventually, IntegrationPatience}

class IntegrationTest extends FunSuite with Eventually with IntegrationPatience {

  val stackName = "AkkaManagementIntegrationTestEC2TagBased"

  val template: String = ??? // the actual akka-cluster.json file (move to the resources folder)

  test("Integration Test for EC2 Tag Based Discovery") {

    val client = AmazonCloudFormationClientBuilder.defaultClient()

    val createStackRequest = new CreateStackRequest()
      .withStackName(stackName)
      .withTemplateBody(template)
      .withParameters(new Parameter().withParameterKey(???).withParameterValue(???))

    val describeStacksRequest = new DescribeStacksRequest()
      .withStackName(stackName)

    val createStackResult: CreateStackResult = client.createStack(createStackRequest)

    var describeStacksResult: DescribeStacksResult = null
    eventually {
      describeStacksResult = client.describeStacks(describeStacksRequest)
      assert(describeStacksResult.getStacks.size() == 1)
      assert(describeStacksResult.getStacks.get(0).getStackStatus == StackStatus.CREATE_COMPLETE.toString,
        "issue in the CloudFormation script"
      )
    }

    // now we should have a real cluster of EC2 instances

    // continue by doing a describe instances request on the auto scaling group
    // will result in a list of IPS

    // use a HTTP client to query each IP:19999/cluster/members
    // make sure the cluster is well formed (no unreachable members, everyone can see everyone etc.)

  }


}