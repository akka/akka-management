/*
 * Copyright (C) 2017-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.discovery.awsapi.ecs

import java.net.{ InetAddress, NetworkInterface }
import java.util.concurrent.TimeoutException

import akka.actor.ActorSystem
import akka.discovery.ServiceDiscovery.{ Resolved, ResolvedTarget }
import akka.discovery.{ Lookup, ServiceDiscovery }
import akka.discovery.awsapi.ecs.AsyncEcsServiceDiscovery._
import akka.pattern.after
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration
import software.amazon.awssdk.core.retry.RetryPolicy
import software.amazon.awssdk.services.ecs._
import software.amazon.awssdk.services.ecs.model._
import scala.collection.JavaConverters._
import scala.collection.immutable.Seq
import scala.compat.java8.FutureConverters._
import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Try

import akka.annotation.ApiMayChange

@ApiMayChange
class AsyncEcsServiceDiscovery(system: ActorSystem) extends ServiceDiscovery {

  private[this] val config = system.settings.config.getConfig("akka.discovery.aws-api-ecs-async")
  private[this] val cluster = config.getString("cluster")
  private[this] val containerName =
    config.getString("container-name") match {
      case "" => None
      case other => Some(other)
    }

  private[this] lazy val ecsClient = {
    val conf = ClientOverrideConfiguration.builder().retryPolicy(RetryPolicy.none).build()
    EcsAsyncClient.builder().overrideConfiguration(conf).build()
  }

  private[this] implicit val ec: ExecutionContext = system.dispatcher

  override def lookup(lookup: Lookup, resolveTimeout: FiniteDuration): Future[Resolved] =
    Future.firstCompletedOf(
      Seq(
        after(resolveTimeout, using = system.scheduler)(
          Future.failed(new TimeoutException("Future timed out!"))
        ),
        resolveTasks(ecsClient, cluster, lookup.serviceName).map(
          tasks =>
            Resolved(
              serviceName = lookup.serviceName,
              addresses = for {
                task <- tasks
                container <- task.containers().asScala
                _ = system.log.debug("Found container '{}' with IP addresses: '{}'", container.name(),
                  container.networkInterfaces().asScala)
                if (containerName.isDefined && container.name() == containerName.get) || containerName.isEmpty
                _ = system.log.debug("Filtered container '{}' with IP addresses: '{}'", container.name(),
                  container.networkInterfaces().asScala)
                networkInterface <- container.networkInterfaces().asScala
              } yield {
                val address = networkInterface.privateIpv4Address()
                ResolvedTarget(host = address, port = None, address = Try(InetAddress.getByName(address)).toOption)
              }
          )
        )
      )
    )

}

@ApiMayChange
object AsyncEcsServiceDiscovery {

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

  private def resolveTasks(ecsClient: EcsAsyncClient, cluster: String, serviceName: String)(
      implicit ec: ExecutionContext): Future[Seq[Task]] =
    for {
      taskArns <- listTaskArns(ecsClient, cluster, serviceName)
      task <- describeTasks(ecsClient, cluster, taskArns)
    } yield task

  private[this] def listTaskArns(
      ecsClient: EcsAsyncClient,
      cluster: String,
      serviceName: String,
      pageTaken: Option[String] = None,
      accumulator: Seq[String] = Seq.empty)(implicit ec: ExecutionContext): Future[Seq[String]] =
    for {
      listTasksResponse <- toScala(
        ecsClient.listTasks(
          ListTasksRequest
            .builder()
            .cluster(cluster)
            .serviceName(serviceName)
            .nextToken(pageTaken.orNull)
            .desiredStatus(DesiredStatus.RUNNING)
            .build()
        )
      )
      accumulatedTasksArns = accumulator ++ listTasksResponse.taskArns().asScala
      taskArns <- listTasksResponse.nextToken() match {
        case null =>
          Future.successful(accumulatedTasksArns)

        case nextPageToken =>
          listTaskArns(
            ecsClient,
            cluster,
            serviceName,
            Some(nextPageToken),
            accumulatedTasksArns
          )
      }
    } yield taskArns

  private[this] def describeTasks(ecsClient: EcsAsyncClient, cluster: String, taskArns: Seq[String])(
      implicit ec: ExecutionContext): Future[Seq[Task]] =
    for {
      // Each DescribeTasksRequest can contain at most 100 task ARNs.
      describeTasksResponses <- Future.traverse(taskArns.grouped(100))(
        taskArnGroup =>
          toScala(
            ecsClient.describeTasks(
              DescribeTasksRequest.builder().cluster(cluster).tasks(taskArnGroup.asJava).build()
            )
        )
      )
      tasks = describeTasksResponses.flatMap(_.tasks().asScala).toList
    } yield tasks

}
