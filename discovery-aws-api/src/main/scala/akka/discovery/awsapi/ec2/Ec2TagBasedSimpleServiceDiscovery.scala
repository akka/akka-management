/*
 * Copyright (C) 2017 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.discovery.awsapi.ec2

import java.util
import java.util.concurrent.TimeoutException

import akka.actor.ActorSystem
import akka.discovery.SimpleServiceDiscovery
import akka.discovery.SimpleServiceDiscovery.{ Resolved, ResolvedTarget }
import akka.discovery.awsapi.ec2.Ec2TagBasedSimpleServiceDiscovery.parseFiltersString
import akka.pattern.after
import com.amazonaws.ClientConfiguration
import com.amazonaws.retry.PredefinedRetryPolicies
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder
import com.amazonaws.services.ec2.model.{ DescribeInstancesRequest, Filter, Reservation }

import scala.collection.JavaConverters._
import scala.collection.immutable.Seq
import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration.FiniteDuration

object Ec2TagBasedSimpleServiceDiscovery {

  private[ec2] def parseFiltersString(filtersString: String): List[Filter] =
    filtersString
      .split(";")
      .filter(_.nonEmpty)
      .map(kv => kv.split("="))
      .toList
      .map(kv => {
        assert(kv.length == 2, "failed to parse one of the key-value pairs in filters")
        new Filter(kv(0), List(kv(1)).asJava)
      })
}

class Ec2TagBasedSimpleServiceDiscovery(system: ActorSystem) extends SimpleServiceDiscovery {

  private val config = system.settings.config.getConfig("akka.discovery.aws-api-ec2-tag-based")

  private[this] implicit val ec: ExecutionContext = system.dispatcher

  private lazy val ec2Client = {
    val clientConfiguration = new ClientConfiguration()
    // we have our own retry/back-off mechanism, so we don't need EC2Client's in addition
    clientConfiguration.setRetryPolicy(PredefinedRetryPolicies.NO_RETRY_POLICY)
    AmazonEC2ClientBuilder.standard().withClientConfiguration(clientConfiguration).build()
  }

  private val tagKey = config.getString("tag-key")

  private val otherFiltersString = config.getString("filters")
  private val otherFilters = parseFiltersString(otherFiltersString)

  private val runningInstancesFilter = new Filter("instance-state-name", List("running").asJava)

  def lookup(name: String): Future[Resolved] = {

    val tagFilter = new Filter("tag:" + tagKey, List(name).asJava)

    val allFilters: util.List[Filter] = (runningInstancesFilter :: tagFilter :: otherFilters).asJava

    val request = new DescribeInstancesRequest().withFilters(allFilters) // withFilters is a set operation

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
        .map((ip: String) => ResolvedTarget(ip, None))
    }.map(Resolved(name, _))

  }

  override def lookup(name: String, resolveTimeout: FiniteDuration): Future[Resolved] =
    Future.firstCompletedOf(
      Seq(
        after(resolveTimeout, using = system.scheduler)(
          Future.failed(new TimeoutException(s"Lookup for [$name] timed-out, within [$resolveTimeout]!"))
        ),
        lookup(name)
      )
    )

}
