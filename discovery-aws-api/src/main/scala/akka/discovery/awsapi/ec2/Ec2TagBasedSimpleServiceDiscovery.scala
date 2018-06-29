/*
 * Copyright (C) 2017 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.discovery.awsapi.ec2

import java.util.concurrent.TimeoutException

import akka.actor.ActorSystem
import akka.discovery.SimpleServiceDiscovery
import akka.discovery.SimpleServiceDiscovery.{ Lookup, Resolved, ResolvedTarget }
import akka.discovery.awsapi.ec2.Ec2TagBasedSimpleServiceDiscovery._
import akka.event.Logging
import akka.pattern.after
import com.amazonaws.ClientConfiguration
import com.amazonaws.retry.PredefinedRetryPolicies
import com.amazonaws.services.ec2.model.{ DescribeInstancesRequest, Filter, Reservation }
import com.amazonaws.services.ec2.{ AmazonEC2, AmazonEC2ClientBuilder }

import scala.annotation.tailrec
import scala.collection.JavaConverters._
import scala.collection.immutable.Seq
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ ExecutionContext, Future }

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

  private val log = Logging(system, classOf[Ec2TagBasedSimpleServiceDiscovery])

  private val ec2Client: AmazonEC2 = {
    val clientConfiguration = new ClientConfiguration()
    // we have our own retry/back-off mechanism, so we don't need EC2Client's in addition
    clientConfiguration.setRetryPolicy(PredefinedRetryPolicies.NO_RETRY_POLICY)
    AmazonEC2ClientBuilder.standard().withClientConfiguration(clientConfiguration).build()
  }

  private implicit val ec: ExecutionContext = system.dispatcher

  private val config = system.settings.config.getConfig("akka.discovery.aws-api-ec2-tag-based")

  private val tagKey = config.getString("tag-key")

  private val otherFiltersString = config.getString("filters")
  private val otherFilters = parseFiltersString(otherFiltersString)

  private val runningInstancesFilter = new Filter("instance-state-name", List("running").asJava)

  @tailrec
  private final def getInstances(client: AmazonEC2,
                                 filters: List[Filter],
                                 nextToken: Option[String],
                                 accumulator: List[String] = Nil): List[String] = {

    val describeInstancesRequest = new DescribeInstancesRequest()
      .withFilters(filters.asJava) // withFilters is a set operation (i.e. calls setFilters, be careful with chaining)
      .withNextToken(nextToken.orNull)

    val describeInstancesResult = client.describeInstances(describeInstancesRequest)

    val ips: List[String] = describeInstancesResult.getReservations.asScala.toList
      .flatMap((r: Reservation) => r.getInstances.asScala.toList)
      .map(instance => instance.getPrivateIpAddress)

    val accumulatedIps = accumulator ++ ips

    Option(describeInstancesResult.getNextToken) match {
      case None =>
        accumulatedIps // aws api has no more results to return, so we return what we have accumulated so far
      case nextPageToken @ Some(_) =>
        // more result items available
        log.debug("aws api returned paginated result, fetching next page!")
        getInstances(client, filters, nextPageToken, accumulatedIps)
    }

  }

  override def lookup(query: Lookup, resolveTimeout: FiniteDuration): Future[Resolved] =
    Future.firstCompletedOf(
      Seq(
        after(resolveTimeout, using = system.scheduler)(
          Future.failed(new TimeoutException(s"Lookup for [$query] timed-out, within [$resolveTimeout]!"))
        ),
        lookup(query)
      )
    )

  def lookup(query: Lookup): Future[Resolved] = {

    val tagFilter = new Filter("tag:" + tagKey, List(query.name).asJava)

    val allFilters: List[Filter] = runningInstancesFilter :: tagFilter :: otherFilters

    Future {
      getInstances(ec2Client, allFilters, None).map((ip: String) => ResolvedTarget(host = ip, port = None))
    }.map(resoledTargets => Resolved(query.name, resoledTargets))

  }

}
