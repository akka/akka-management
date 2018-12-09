/*
 * Copyright (C) 2017-2018 Lightbend Inc. <http://www.lightbend.com>
 */

package akka.discovery.awsapi.ec2

import java.util.concurrent.TimeoutException

import akka.actor.ExtendedActorSystem
import akka.annotation.InternalApi
import akka.discovery.SimpleServiceDiscovery.{ Resolved, ResolvedTarget }
import akka.discovery.awsapi.ec2.Ec2TagBasedSimpleServiceDiscovery._
import akka.discovery.{ Lookup, SimpleServiceDiscovery }
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
import scala.util.{ Failure, Success }

/** INTERNAL API */
@InternalApi private[ec2] object Ec2TagBasedSimpleServiceDiscovery {

  private[ec2] def parseFiltersString(filtersString: String): List[Filter] =
    filtersString
      .split(";")
      .filter(_.nonEmpty)
      .map(kv ⇒ kv.split("="))
      .toList
      .map(kv ⇒ {
        assert(kv.length == 2, "failed to parse one of the key-value pairs in filters")
        new Filter(kv(0), List(kv(1)).asJava)
      })

}

class Ec2TagBasedSimpleServiceDiscovery(system: ExtendedActorSystem) extends SimpleServiceDiscovery {

  private val log = Logging(system, classOf[Ec2TagBasedSimpleServiceDiscovery])

  private val ec2Client: AmazonEC2 = {
    val clientConfiguration = new ClientConfiguration()
    // we have our own retry/back-off mechanism, so we don't need EC2Client's in addition
    clientConfiguration.setRetryPolicy(PredefinedRetryPolicies.NO_RETRY_POLICY)
    AmazonEC2ClientBuilder.standard().withClientConfiguration(clientConfiguration).build()
  }

  private implicit val ec: ExecutionContext = system.dispatchers.lookup("akka.actor.default-blocking-io-dispatcher")

  private val config = system.settings.config.getConfig("akka.discovery.aws-api-ec2-tag-based")

  private val clientConfigFqcn: Option[String] = { // FQCN of a class that extends com.amazonaws.ClientConfiguration
    if (config.hasPath("client-config")) {
      Some(config.getString("client-config"))
    } else {
      None
    }
  }

  private val tagKey = config.getString("tag-key")

  private val otherFiltersString = config.getString("filters")
  private val otherFilters = parseFiltersString(otherFiltersString)

  private val preDefinedPorts =
    config.getIntList("ports").asScala.toList match {
      case Nil ⇒ None
      case list ⇒ Some(list) // Akka Management ports
    }

  private val runningInstancesFilter = new Filter("instance-state-name", List("running").asJava)

  private val defaultClientConfiguration = {
    val clientConfiguration = new ClientConfiguration()
    // we have our own retry/back-off mechanism (in Cluster Bootstrap), so we don't need EC2Client's in addition
    clientConfiguration.setRetryPolicy(PredefinedRetryPolicies.NO_RETRY_POLICY)
    clientConfiguration
  }

  private val ec2Client: AmazonEC2 = {
    val clientConfiguration = clientConfigFqcn match {
      case Some(fqcn) ⇒
        system.dynamicAccess.createInstanceFor[ClientConfiguration](fqcn, Nil) match {
          case Success(clientConfig) ⇒
            if (clientConfig.getRetryPolicy != PredefinedRetryPolicies.NO_RETRY_POLICY) {
              log.warning(
                  "Akka Cluster Bootstrap has its own retry/back-off mechanism, to avoid RequestLimitExceeded errors from AWS, " +
                  "disable retries in the EC2 client configuration")
            }
            clientConfig
          case Failure(ex) ⇒
            throw new Exception(s"Could not create instance of '$fqcn'", ex)
        }
      case None ⇒
        defaultClientConfiguration
    }
    AmazonEC2ClientBuilder.standard().withClientConfiguration(clientConfiguration).build()
  }

  @tailrec
  private final def getInstances(
      client: AmazonEC2,
      filters: List[Filter],
      nextToken: Option[String],
      accumulator: List[String] = Nil
  ): List[String] = {

    val describeInstancesRequest = new DescribeInstancesRequest()
      .withFilters(filters.asJava) // withFilters is a set operation (i.e. calls setFilters, be careful with chaining)
      .withNextToken(nextToken.orNull)

    val describeInstancesResult = client.describeInstances(describeInstancesRequest)

    val ips: List[String] =
      describeInstancesResult.getReservations.asScala.toList
        .flatMap((r: Reservation) ⇒ r.getInstances.asScala.toList)
        .map(instance ⇒ instance.getPrivateIpAddress)

    val accumulatedIps = accumulator ++ ips

    Option(describeInstancesResult.getNextToken) match {
      case None ⇒
        accumulatedIps // aws api has no more results to return, so we return what we have accumulated so far
      case nextPageToken @ Some(_) ⇒
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

    val tagFilter = new Filter("tag:" + tagKey, List(query.serviceName).asJava)

    val allFilters: List[Filter] = runningInstancesFilter :: tagFilter :: otherFilters

    Future {
      getInstances(ec2Client, allFilters, None).flatMap(
        (ip: String) ⇒
          preDefinedPorts match {
            case None ⇒ ResolvedTarget(host = ip, port = None) :: Nil
            case Some(ports) ⇒
              ports.map(p ⇒ ResolvedTarget(host = ip, port = Some(p))) // this allows multiple akka nodes (i.e. JVMs) per EC2 instance
        }
      )
    }.map(resoledTargets ⇒ Resolved(query.serviceName, resoledTargets))

  }

}
