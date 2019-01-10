package akka.management.http
import akka.actor.ExtendedActorSystem
import akka.annotation.InternalApi
import akka.event.Logging
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{PathMatchers, Route}
import akka.management.http.scaladsl.{HealthChecks, ManagementRouteProvider}
import com.typesafe.config.Config

import scala.collection.JavaConverters._
import scala.collection.immutable
import scala.util.{Failure, Success, Try}

object HealthCheckSettings {
  def apply(config: Config): HealthCheckSettings =
    new HealthCheckSettings(
      config.getStringList("readiness-checks").asScala.toList,
      config.getStringList("liveness-checks").asScala.toList,
      config.getString("readiness-path"),
      config.getString("liveness-path")
    )

  def create(readinessChecks: java.util.List[String],
             livenessChecks: java.util.List[String],
             readinessPath: String,
             livenessPath: String
            ) = new HealthCheckSettings(readinessChecks.asScala.toList,
    livenessChecks.asScala.toList, readinessPath, livenessPath)
}

final class HealthCheckSettings(val readinessChecks: immutable.Seq[String],
                                val livenessChecks: immutable.Seq[String],
                                val readinessPath: String,
                                val livenessPath: String)

class HealthCheckRoutes(aes: ExtendedActorSystem)
    extends ManagementRouteProvider {

  val settings: HealthCheckSettings = HealthCheckSettings(
    aes.settings.config.getConfig("akka.management.http.health-checks")
  )

  // exposed for testing
  protected val healthChecks = HealthChecks(aes, settings)

  private val healthCheckResponse: Try[Boolean] => Route = {
    case Success(true) => complete(StatusCodes.OK)
    case Success(false) =>
      complete(StatusCodes.InternalServerError -> "Not Healthy")
    case Failure(t) =>
      complete(
        StatusCodes.InternalServerError -> s"Health Check Failed: ${t.getMessage}"
      )
  }

  override def routes(mrps: ManagementRouteProviderSettings): Route = {
    concat(path(PathMatchers.separateOnSlashes(settings.readinessPath)) {
      get {
        onComplete(healthChecks.ready())(healthCheckResponse)
      }
    }, path(PathMatchers.separateOnSlashes(settings.livenessPath)) {
      get {
        onComplete(healthChecks.alive())(healthCheckResponse)
      }
    })
  }
}

