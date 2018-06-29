/*
 * Copyright (C) 2017 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.discovery.awsapi.ecs

import java.net.{ InetAddress, NetworkInterface }
import java.util.concurrent.TimeoutException

import akka.actor.ActorSystem
import akka.discovery.SimpleServiceDiscovery
import akka.discovery.SimpleServiceDiscovery.{ Lookup, Resolved, ResolvedTarget }
import akka.discovery.awsapi.ecs.EcsSimpleServiceDiscovery._
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

class EcsSimpleServiceDiscovery(system: ActorSystem) extends SimpleServiceDiscovery {

  private[this] val config = system.settings.config.getConfig("akka.discovery.aws-api-ecs")
  private[this] val cluster = config.getString("cluster")

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
            serviceName = query.name,
            addresses = for {
              task <- resolveTasks(ecsClient, cluster, query.name)
              container <- task.getContainers.asScala
              networkInterface <- container.getNetworkInterfaces.asScala
            } yield
              ResolvedTarget(
                host = networkInterface.getPrivateIpv4Address,
                port = None
              )
          )
        }
      )
    )

}

object EcsSimpleServiceDiscovery {

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
      .to[Seq] match {
      case Seq(value) =>
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
      group <- taskArns.grouped(100).to[Seq]
      tasks = ecsClient.describeTasks(
        new DescribeTasksRequest().withCluster(cluster).withTasks(group.asJava)
      )
      task <- tasks.getTasks.asScala
    } yield task

}
