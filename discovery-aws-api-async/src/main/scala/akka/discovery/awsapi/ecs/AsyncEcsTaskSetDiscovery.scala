/*
 * Copyright (C) 2017-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.discovery.awsapi.ecs

import java.net.InetAddress
import java.util.concurrent.TimeoutException

import scala.collection.JavaConverters._
import scala.collection.immutable.Seq
import scala.compat.java8.FutureConverters.toScala
import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Try

import akka.actor.ActorSystem
import akka.annotation.ApiMayChange
import akka.discovery.ServiceDiscovery.{ Resolved, ResolvedTarget }
import akka.discovery.awsapi.ecs.AsyncEcsTaskSetDiscovery._
import akka.discovery.{ Lookup, ServiceDiscovery }
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.http.scaladsl.{ Http, HttpExt }
import akka.pattern.after
import akka.stream.Materializer
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration
import software.amazon.awssdk.core.retry.RetryPolicy
import software.amazon.awssdk.services.ecs._
import software.amazon.awssdk.services.ecs.model.{
  DescribeTasksRequest,
  DesiredStatus,
  ListTasksRequest,
  Task,
  TaskField,
  Tag => _
}
import spray.json.DefaultJsonProtocol._
import spray.json.RootJsonFormat

@ApiMayChange
class AsyncEcsTaskSetDiscovery(system: ActorSystem) extends ServiceDiscovery {

  private val config = system.settings.config.getConfig("akka.discovery.aws-api-ecs-task-set-async")
  private val cluster = config.getString("cluster")

  private lazy val ecsClient = {
    val conf = ClientOverrideConfiguration.builder().retryPolicy(RetryPolicy.none).build()
    EcsAsyncClient.builder().overrideConfiguration(conf).build()
  }

  private implicit val actorSystem: ActorSystem = system
  private implicit val ec: ExecutionContext = system.dispatcher

  private val httpClient: HttpExt = Http()

  override def lookup(lookup: Lookup, resolveTimeout: FiniteDuration): Future[Resolved] =
    Future.firstCompletedOf(
      Seq(
        after(resolveTimeout, using = system.scheduler)(
          Future.failed(new TimeoutException(s"$lookup timed out after $resolveTimeout"))
        ),
        resolveTasks(ecsClient, cluster, httpClient).map(tasks =>
          Resolved(
            serviceName = lookup.serviceName,
            addresses = for {
              task <- tasks
              container <- task.containers().asScala
              networkInterface <- container.networkInterfaces().asScala
            } yield {
              val address = networkInterface.privateIpv4Address()
              ResolvedTarget(host = address, port = None, address = Try(InetAddress.getByName(address)).toOption)
            }
          ))
      )
    )

}

@ApiMayChange
object AsyncEcsTaskSetDiscovery {

  private[this] case class TaskMetadata(TaskARN: String)
  private[this] case class TaskSet(value: String) extends AnyVal

  private[this] implicit val orderFormat: RootJsonFormat[TaskMetadata] = jsonFormat1(TaskMetadata.apply)

  private val ECS_CONTAINER_METADATA_URI_PATH = "ECS_CONTAINER_METADATA_URI"

  private def resolveTasks(ecsClient: EcsAsyncClient, cluster: String, httpClient: HttpExt)(
      implicit
      ec: ExecutionContext,
      mat: Materializer
  ): Future[Seq[Task]] =
    for {
      taskArn <- resolveTaskMetadata(httpClient).map(_.map(_.TaskARN))
      taskSet <- taskArn match {
        case Some(arn) => resolveTaskSet(ecsClient, cluster, arn)
        case None      => Future.successful(None)
      }
      taskArns <- taskSet match {
        case Some(ts) => listTaskArns(ecsClient, cluster, ts)
        case None     => Future.successful(Seq.empty[String])
      }
      tasks <- describeTasks(ecsClient, cluster, taskArns)
    } yield tasks

  // https://docs.aws.amazon.com/AmazonECS/latest/developerguide/task-metadata-endpoint-v3.html
  private[this] def resolveTaskMetadata(httpClient: HttpExt)(
      implicit
      ec: ExecutionContext,
      mat: Materializer
  ): Future[Option[TaskMetadata]] = {
    val ecsContainerMetadataUri = sys.env.get(ECS_CONTAINER_METADATA_URI_PATH) match {
      case Some(uri) => uri
      case None =>
        throw new IllegalStateException("The environment variable ECS_CONTAINER_METADATA_URI cannot be found")
    }

    httpClient.singleRequest(HttpRequest(uri = s"$ecsContainerMetadataUri/task")).flatMap {
      case HttpResponse(StatusCodes.OK, _, entity, _) =>
        val metadata = Unmarshal(entity).to[TaskMetadata].map(Option(_))
        metadata
      case resp @ HttpResponse(_, _, _, _) =>
        resp.discardEntityBytes()
        Future.successful(None)
    }
  }

  private[this] def resolveTaskSet(ecsClient: EcsAsyncClient, cluster: String, taskArn: String)(
      implicit ec: ExecutionContext
  ): Future[Option[TaskSet]] =
    toScala(
      ecsClient.describeTasks(
        DescribeTasksRequest.builder().cluster(cluster).tasks(taskArn).include(TaskField.TAGS).build()
      )
    ).map(_.tasks().asScala.headOption).map(_.map(task => TaskSet(task.startedBy())))

  private[this] def listTaskArns(
      ecsClient: EcsAsyncClient,
      cluster: String,
      taskSet: TaskSet,
      pageTaken: Option[String] = None,
      accumulator: Seq[String] = Seq.empty
  )(implicit ec: ExecutionContext): Future[Seq[String]] =
    for {
      listTasksResponse <- toScala(
        ecsClient.listTasks(
          ListTasksRequest
            .builder()
            .cluster(cluster)
            .startedBy(taskSet.value)
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
            taskSet,
            Some(nextPageToken),
            accumulatedTasksArns
          )
      }
    } yield taskArns

  private[this] def describeTasks(ecsClient: EcsAsyncClient, cluster: String, taskArns: Seq[String])(
      implicit ec: ExecutionContext
  ): Future[Seq[Task]] =
    for {
      // Each DescribeTasksRequest can contain at most 100 task ARNs.
      describeTasksResponses <- Future.traverse(taskArns.grouped(100))(taskArnGroup =>
        toScala(
          ecsClient.describeTasks(
            DescribeTasksRequest.builder().cluster(cluster).tasks(taskArnGroup.asJava).include(TaskField.TAGS).build()
          )
        ))
      tasks = describeTasksResponses.flatMap(_.tasks().asScala).toList
    } yield tasks

}
