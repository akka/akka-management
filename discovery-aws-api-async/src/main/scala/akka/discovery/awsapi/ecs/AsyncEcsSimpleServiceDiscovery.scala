/*
 * Copyright (C) 2017 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.discovery.awsapi.ecs

import java.net.{ InetAddress, NetworkInterface }
import java.util.concurrent.TimeoutException

import akka.actor.ActorSystem
import akka.discovery.SimpleServiceDiscovery
import akka.discovery.SimpleServiceDiscovery.{ Resolved, ResolvedTarget }
import akka.discovery.awsapi.ecs.AsyncEcsSimpleServiceDiscovery._
import akka.pattern.after
import software.amazon.awssdk.services.ecs._
import software.amazon.awssdk.services.ecs.model._

import scala.collection.JavaConverters._
import scala.collection.immutable.Seq
import scala.compat.java8.FutureConverters._
import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }

class AsyncEcsSimpleServiceDiscovery(system: ActorSystem) extends SimpleServiceDiscovery {

  private[this] implicit val ec: ExecutionContext = system.dispatcher

  private[this] lazy val ecsClient = ECSAsyncClient.create()

  override def lookup(name: String, resolveTimeout: FiniteDuration): Future[Resolved] =
    Future.firstCompletedOf(
      Seq(
        after(resolveTimeout, using = system.scheduler)(
          Future.failed(new TimeoutException("Future timed out!"))
        ),
        resolveTasks(ecsClient, name).map(
          tasks =>
            Resolved(
              serviceName = name,
              addresses = for {
                task <- tasks
                container <- task.containers().asScala
                networkInterface <- container.networkInterfaces().asScala
              } yield
                ResolvedTarget(
                  host = networkInterface.privateIpv4Address(),
                  port = None
                )
          )
        )
      )
    )

}

object AsyncEcsSimpleServiceDiscovery {

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

  private def resolveTasks(ecsClient: ECSAsyncClient, serviceName: String)(
      implicit ec: ExecutionContext): Future[Seq[Task]] =
    for {
      taskArns <- listTaskArns(ecsClient, serviceName)
      task <- describeTasks(ecsClient, taskArns)
    } yield task

  private[this] def listTaskArns(
      ecsClient: ECSAsyncClient,
      serviceName: String,
      pageTaken: Option[String] = None,
      accumulator: Seq[String] = Seq.empty)(implicit ec: ExecutionContext): Future[Seq[String]] =
    for {
      listTasksResponse <- toScala(
        ecsClient.listTasks(
          ListTasksRequest
            .builder()
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
            serviceName,
            Some(nextPageToken),
            accumulatedTasksArns
          )
      }
    } yield taskArns

  private[this] def describeTasks(ecsClient: ECSAsyncClient, taskArns: Seq[String])(
      implicit ec: ExecutionContext): Future[Seq[Task]] =
    for {
      // Each DescribeTasksRequest can contain at most 100 task ARNs.
      describeTasksResponses <- Future.traverse(taskArns.grouped(100))(
        taskArnGroup =>
          toScala(
            ecsClient.describeTasks(
              DescribeTasksRequest.builder().tasks(taskArnGroup.asJava).build()
            )
        )
      )
      tasks = describeTasksResponses.flatMap(_.tasks().asScala).to[Seq]
    } yield tasks

}
