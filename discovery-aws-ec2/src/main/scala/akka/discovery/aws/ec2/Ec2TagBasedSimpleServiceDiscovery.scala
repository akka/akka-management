/*
 * Copyright (C) 2017 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.discovery.aws.ec2

import akka.actor.ActorSystem
import akka.discovery.SimpleServiceDiscovery
import akka.discovery.SimpleServiceDiscovery.{Resolved, ResolvedTarget}
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder
import com.amazonaws.services.ec2.model.{DescribeInstancesRequest, Filter}

import scala.collection.JavaConverters._
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

class Ec2TagBasedSimpleServiceDiscovery(system: ActorSystem) extends SimpleServiceDiscovery {

  override def lookup(name: String, resolveTimeout: FiniteDuration): Future[Resolved] = {

    val ec2Client = AmazonEC2ClientBuilder.defaultClient

    val tagKey = system.settings.config.getConfig("akka.discovery.aws-ec2-tag-based").getString("tag-key")
    val tagFilter = new Filter("tag:" + tagKey, List(name).asJava)

    val runningInstancesFilter = new Filter("instance-state-name", List("running").asJava)

    val request = new DescribeInstancesRequest().withFilters(tagFilter).withFilters(runningInstancesFilter)

    implicit val timeout = resolveTimeout

    import system.dispatcher

    // pretty quick call on EC2, not worried about blocking
    Future {
      ec2Client
        .describeInstances(request)
        .getReservations
        .asScala.toList
        .flatMap(_.getInstances.asScala.toList)
        .map(_.getPrivateIpAddress)
        .map(ip => ResolvedTarget(ip, None))
    }.map(Resolved(name, _))

  }

}
