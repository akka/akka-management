package akka.management.http
import akka.actor.ExtendedActorSystem
import akka.annotation.InternalApi
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{Directive1, PathMatchers, Route}
import akka.management.http.scaladsl.ManagementRouteProvider
import com.typesafe.config.Config

import scala.collection.JavaConverters._
import scala.collection.immutable
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

object HealthCheckSettings {
  def apply(config: Config): HealthCheckSettings =
    new HealthCheckSettings(
      config.getStringList("readiness-checks").asScala.toList,
      config.getStringList("liveness-checks").asScala.toList,
      config.getString("readiness-path"),
      config.getString("liveness-path")
    )
}

final class HealthCheckSettings(val readinessChecks: immutable.Seq[String],
                                val liveness: immutable.Seq[String],
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

object HealthChecks {
  def apply(system: ExtendedActorSystem,
            settings: HealthCheckSettings): HealthChecks =
    new HealthChecksImpl(system, settings)

  type HealthCheck = () => Future[Boolean]

  class InvalidHealthCheckException(msg: String, c: Throwable)
      extends RuntimeException(msg, c) {
    def this(msg: String) = this(msg, null)
  }
}

trait HealthChecks {
  def ready(): Future[Boolean]
  def alive(): Future[Boolean]
}

@InternalApi
private[akka] class HealthChecksImpl(system: ExtendedActorSystem,
                                     settings: HealthCheckSettings)
    extends HealthChecks {
  import HealthChecks._

  import system.dispatcher

  // TODO support various shapes e.g. Java Function with CompletionStage
  private val readiness: immutable.Seq[HealthCheck] = load(
    settings.readinessChecks
  )
  private val liveness: immutable.Seq[HealthCheck] = load(settings.liveness)

  private def load(
    checks: immutable.Seq[String]
  ): immutable.Seq[HealthCheck] = {
    checks
      .map(
        fqcn =>
          system.dynamicAccess.createInstanceFor[HealthCheck](
            fqcn,
            immutable.Seq((classOf[ExtendedActorSystem], system))
        )
      )
      .map {
        case Success(c) => c
        case Failure(_: NoSuchMethodException) =>
          throw new InvalidHealthCheckException(
            s"Health checks: [${checks.mkString(",")}] must have a single argument constructor that takes an ExtendedActorSystem"
          )
        case Failure(_: ClassCastException) =>
          throw new InvalidHealthCheckException(
            s"Health checks: [${checks.mkString(",")}] must have type: () => Future[Boolean]"
          )
        case Failure(c: ClassNotFoundException) =>
          throw new InvalidHealthCheckException(
            s"Health check: [${c.getMessage}] not found"
          )
        case Failure(t) =>
          throw new InvalidHealthCheckException(
            "Uncaught exception from Health check construction",
            t
          )
      }
  }

  def ready(): Future[Boolean] = {
    check(readiness)
  }

  def alive(): Future[Boolean] = {
    check(liveness)
  }

  private def check(checks: immutable.Seq[HealthCheck]): Future[Boolean] = {
    Future.traverse(checks)(check => check()).map(_.forall(identity))
  }
}
