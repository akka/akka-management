/*
 * Copyright (C) 2017-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.discovery.awsapi.ecs

import java.net.{ InetAddress, NetworkInterface }
import java.util.concurrent.TimeoutException

import akka.actor.ActorSystem
import akka.discovery.ServiceDiscovery.{ Resolved, ResolvedTarget }
import akka.discovery.{ Lookup, ServiceDiscovery }
import akka.discovery.awsapi.ecs.EcsServiceDiscovery._
import akka.pattern.after
import com.amazonaws.ClientConfiguration
import com.amazonaws.retry.PredefinedRetryPolicies
import com.amazonaws.services.ecs.model.{ DescribeTasksRequest, DesiredStatus, ListTasksRequest, Task }
import com.amazonaws.services.ecs.{ AmazonECS, AmazonECSClientBuilder }
import scala.annotation.tailrec
import scala.collection.JavaConverters._
import scala.collection.immutable.Seq
import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration._
import scala.util.Try

import akka.annotation.ApiMayChange

@ApiMayChange
class EcsServiceDiscovery(system: ActorSystem) extends ServiceDiscovery {

  private[this] val config = system.settings.config.getConfig("akka.discovery.aws-api-ecs")
  private[this] val cluster = config.getString("cluster")
  private[this] val containerName =
    config.getString("container-name") match {
      case "" => None
      case other => Some(other)
    }

  private[this] lazy val ecsClient = {
    // we have our own retry/backoff mechanism, so we don't need EC2Client's in addition
    val clientConfiguration = new ClientConfiguration()
    clientConfiguration.setRetryPolicy(PredefinedRetryPolicies.NO_RETRY_POLICY)
    AmazonECSClientBuilder.standard().withClientConfiguration(clientConfiguration).build()
  }

  private[this] implicit val ec: ExecutionContext = system.dispatcher

  override def lookup(query: Lookup, resolveTimeout: FiniteDuration): Future[Resolved] =
    Future.firstCompletedOf(
      Seq(
        after(resolveTimeout, using = system.scheduler)(
          Future.failed(new TimeoutException("Future timed out!"))
        ),
        Future {
          Resolved(
            serviceName = query.serviceName,
            addresses = for {
              task <- resolveTasks(ecsClient, cluster, query.serviceName)
              container <- task.getContainers.asScala
              _ = system.log.debug("Found container '{}' with IP addresses: '{}'", container.getName,
                container.getNetworkInterfaces.asScala)
              if (containerName.isDefined && container.getName == containerName.get) || containerName.isEmpty
              _ = system.log.debug("Filtered container '{}' with IP addresses: '{}'", container.getName,
                container.getNetworkInterfaces.asScala)
              networkInterface <- container.getNetworkInterfaces.asScala
            } yield {
              val address = networkInterface.getPrivateIpv4Address
              ResolvedTarget(host = address, port = None, address = Try(InetAddress.getByName(address)).toOption)
            }
          )
        }
      )
    )

}

@ApiMayChange
object EcsServiceDiscovery {

  // InetAddress.getLocalHost.getHostAddress throws an exception when running
  // in awsvpc mode because the container name cannot be resolved.
  // ECS provides a metadata file
  // (https://docs.aws.amazon.com/AmazonECS/latest/developerguide/container-metadata.html)
  // that we ought to be able to use instead to find our IP address, but the
  // metadata file does not get set when running on Fargate. So this is our
  // only option for determining what the canonical Akka and akka-management
  // hostname values should be set to.
  def getContainerAddress: Either[String, InetAddress] =
    NetworkInterface.getNetworkInterfaces.asScala
      .flatMap(_.getInetAddresses.asScala)
      .filterNot(_.isLoopbackAddress)
      .filter(_.isSiteLocalAddress)
      .toList match {
      case List(value) =>
        Right(value)

      case other =>
        Left(s"Exactly one private address must be configured (found: $other).")
    }

  private def resolveTasks(ecsClient: AmazonECS, cluster: String, serviceName: String): Seq[Task] = {
    val taskArns = listTaskArns(ecsClient, cluster, serviceName)
    val tasks = describeTasks(ecsClient, cluster, taskArns)
    tasks
  }

  @tailrec private[this] def listTaskArns(ecsClient: AmazonECS,
                                          cluster: String,
                                          serviceName: String,
                                          pageTaken: Option[String] = None,
                                          accumulator: Seq[String] = Seq.empty): Seq[String] = {
    val listTasksResult = ecsClient.listTasks(
      new ListTasksRequest()
        .withCluster(cluster)
        .withServiceName(serviceName)
        .withNextToken(pageTaken.orNull)
        .withDesiredStatus(DesiredStatus.RUNNING)
    )
    val accumulatedTasksArns = accumulator ++ listTasksResult.getTaskArns.asScala
    listTasksResult.getNextToken match {
      case null =>
        accumulatedTasksArns

      case nextPageToken =>
        listTaskArns(
          ecsClient,
          cluster,
          serviceName,
          Some(nextPageToken),
          accumulatedTasksArns
        )
    }
  }

  private[this] def describeTasks(ecsClient: AmazonECS, cluster: String, taskArns: Seq[String]): Seq[Task] =
    for {
      // Each DescribeTasksRequest can contain at most 100 task ARNs.
      group <- taskArns.grouped(100).toList
      tasks = ecsClient.describeTasks(
        new DescribeTasksRequest().withCluster(cluster).withTasks(group.asJava)
      )
      task <- tasks.getTasks.asScala
    } yield task

}
