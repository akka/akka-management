/*
 * Copyright (C) 2017 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.discovery.awsapi.ec2

import java.util

import akka.actor.ActorSystem
import akka.discovery.SimpleServiceDiscovery
import akka.discovery.SimpleServiceDiscovery.{ Resolved, ResolvedTarget }
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder
import com.amazonaws.services.ec2.model.{ DescribeInstancesRequest, Filter, Reservation }

import scala.collection.JavaConverters._
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

class Ec2TagBasedSimpleServiceDiscovery(system: ActorSystem) extends SimpleServiceDiscovery {

  private[ec2] def parseFiltersString(filtersString: String): List[Filter] =
    filtersString match {
      case "" => List[Filter]()
      case value =>
        filtersString
          .split(";")
          .map(kv => kv.split("="))
          .toList
          .map(kv => {
            assert(kv.length == 2, "failed to parse one of the key-value pairs in filters")
            new Filter(kv(0), List(kv(1)).asJava)
          })
    }

  override def lookup(name: String, resolveTimeout: FiniteDuration): Future[Resolved] = {

    val ec2Client = AmazonEC2ClientBuilder.defaultClient

    val tagKey = system.settings.config.getConfig("akka.discovery.aws-api-ec2-tag-based").getString("tag-key")
    val tagFilter = new Filter("tag:" + tagKey, List(name).asJava)

    val runningInstancesFilter = new Filter("instance-state-name", List("running").asJava)

    val otherFiltersString =
      system.settings.config.getConfig("akka.discovery.aws-api-ec2-tag-based").getString("filters")

    val otherFilters: util.List[Filter] = parseFiltersString(otherFiltersString).asJava

    val request = new DescribeInstancesRequest()
      .withFilters(runningInstancesFilter)
      .withFilters(tagFilter)
      .withFilters(otherFilters)

    implicit val timeout: FiniteDuration = resolveTimeout

    import system.dispatcher

    // pretty quick call on EC2, not worried about blocking
    Future {
      // TODO: handle pagination (in rare case you get hundreds of instances, the results of this call come
      // paginated and the user of the API has to call describeInstances again, with the nextToken
      ec2Client
        .describeInstances(request)
        .getReservations
        .asScala
        .toList
        .flatMap((r: Reservation) => r.getInstances.asScala.toList)
        .map(instance => instance.getPrivateIpAddress)
        .filter(ip => ip != null) // have observed some behaviour where the IP address is null (perhaps terminated
        // instances were included ?
        // TODO: investigate if the null check is really necessary
        .map((ip: String) => ResolvedTarget(ip, None))
    }.map(Resolved(name, _))

  }

}
