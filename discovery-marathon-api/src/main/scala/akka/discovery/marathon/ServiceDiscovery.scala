/*
 * Copyright (C) 2017 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.discovery.marathon

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ ExecutionContext, Future }

import akka.actor.ActorSystem
import akka.discovery.SimpleServiceDiscovery.ResolvedTarget
import akka.discovery.marathon.resolvers.{ Apps, Pods }
import akka.discovery.marathon.services.{ App, MarathonService, Pod }
import akka.http.scaladsl.HttpExt
import akka.http.scaladsl.model.{ HttpEntity, HttpRequest, Uri }
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer

trait ServiceDiscovery[T <: MarathonService] {

  protected implicit val system: ActorSystem
  protected val http: HttpExt

  protected implicit val ec: ExecutionContext = system.dispatcher
  protected implicit val mat: ActorMaterializer = ActorMaterializer()

  /**
   * Queries the Marathon API and retrieves a list of targets for the specified service (actor system) name.
   *
   * @param serviceName target akka cluster actor system name
   * @param timeout API request timeout
   * @return the resolved targets
   */
  def resolveTargets(serviceName: String)(
      implicit timeout: FiniteDuration
  ): Future[Seq[ResolvedTarget]] = {
    request(apiUri,
      timeout).map(services => filter(services, serviceName)).map(services => process(services, serviceName))
  }

  /**
   * Generates the appropriate API URI for the specific service type.
   *
   * @return the requested URI
   */
  protected def apiUri: Uri

  /**
   * Examines the supplied data and generates a list of targets.
   *
   * @param data the data to examine
   * @param serviceName target akka cluster actor system name
   * @return the resolved targets
   */
  protected def process(data: Seq[T], serviceName: String): Seq[ResolvedTarget]

  /**
   * Filters the supplied data based on the specified service (actor system) name.
   *
   * @param data the data to filter
   * @param serviceName target akka cluster actor system name
   * @return the filtered data
   */
  protected def filter(data: Seq[T], serviceName: String): Seq[T]

  /**
   * Converts the supplied http entity to a Marathon services list.
   *
   * @param entity the entity to process
   * @return the services list
   */
  protected def unmarshal(entity: HttpEntity.Strict): Future[Seq[T]]

  /**
   * Sends a request to the specified Marathon API.
   *
   * @param uri the URI to query
   * @param timeout API request timeout
   * @return the API response as a list of services
   */
  protected def request(uri: Uri, timeout: FiniteDuration): Future[Seq[T]] = {
    for {
      response <- http.singleRequest(HttpRequest(uri = uri))

      entity <- response.entity.toStrict(timeout)

      data <- {
        system.log.debug(
          "Received response from Marathon API [{}] with status: [{}] and entity: [{}]",
          uri,
          response.status.value,
          entity.data.utf8String
        )

        val unmarshalled = unmarshal(entity)

        unmarshalled.failed.foreach { e =>
          system.log.error(
            e,
            "Failed to unmarshal response from Marathon API [{}] with status: [{}] and entity: [{}]",
            uri,
            response.status.value,
            entity.data.utf8String
          )
        }
        unmarshalled
      }
    } yield {
      data
    }
  }
}

object ServiceDiscovery {

  import akka.discovery.marathon.services.JsonFormat._

  /**
   * Service discovery for Marathon Apps.
   */
  final class ForApps(val settings: Settings)(implicit override protected val system: ActorSystem,
                                              override protected val http: HttpExt)
      extends ServiceDiscovery[App] {

    override protected def apiUri: Uri =
      Uri(s"${settings.marathonApiUrl}/v2/apps").withQuery(
        Uri.Query("embed" -> "apps.tasks")
      )

    override protected def unmarshal(
        entity: HttpEntity.Strict
    ): Future[Seq[App]] =
      Unmarshal(entity).to[AppList].map(_.apps)

    override protected def filter(
        data: Seq[App],
        serviceName: String
    ): Seq[App] = {
      data.filter { app =>
        app.labels.get(settings.serviceLabelName).contains(serviceName)
      }
    }

    protected override def process(
        data: Seq[App],
        serviceName: String
    ): Seq[ResolvedTarget] = {
      Apps.resolve(
        data,
        settings,
        serviceName
      )
    }
  }

  /**
   * Service discovery for Marathon Pods.
   */
  final class ForPods(val settings: Settings)(
      implicit override protected val system: ActorSystem,
      override protected val http: HttpExt
  ) extends ServiceDiscovery[Pod] {

    override protected def apiUri: Uri =
      Uri(s"${settings.marathonApiUrl}/v2/pods/::status")

    override protected def unmarshal(
        entity: HttpEntity.Strict
    ): Future[Seq[Pod]] =
      Unmarshal(entity).to[Seq[Pod]]

    override protected def filter(
        data: Seq[Pod],
        serviceName: String
    ): Seq[Pod] = {
      data.filter { pod =>
        pod.spec.labels.exists(_.get(settings.serviceLabelName).contains(serviceName))
      }
    }

    override def process(
        data: Seq[Pod],
        serviceName: String
    ): Seq[ResolvedTarget] =
      Pods.resolve(
        data,
        settings,
        serviceName
      )
  }

}
