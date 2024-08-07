/*
 * Copyright (C) 2017-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.management

import akka.actor.ExtendedActorSystem
import akka.annotation.InternalApi
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{ PathMatchers, Route }
import akka.management.scaladsl.{ HealthChecks, ManagementRouteProvider, ManagementRouteProviderSettings }

import scala.util.{ Failure, Success, Try }

/**
 * INTERNAL API
 *
 * We could make this public so users can add it to their own server, not sure
 * for ManagementRouteProviders
 */
@InternalApi
private[akka] class HealthCheckRoutes(system: ExtendedActorSystem) extends ManagementRouteProvider {

  private val settings: HealthCheckSettings = HealthCheckSettings(
    system.settings.config.getConfig("akka.management.health-checks")
  )

  // exposed for testing
  protected val healthChecks = HealthChecks(system, settings)

  private val healthCheckResponse: Try[Either[String, Unit]] => Route = {
    case Success(Right(())) => complete(StatusCodes.OK)
    case Success(Left(failingChecks)) =>
      complete(StatusCodes.InternalServerError -> s"Not Healthy: $failingChecks")
    case Failure(t) =>
      complete(
        StatusCodes.InternalServerError -> s"Health Check Failed: ${t.getMessage}"
      )
  }

  override def routes(mrps: ManagementRouteProviderSettings): Route = {
    concat(
      path(PathMatchers.separateOnSlashes(settings.readinessPath)) {
        get {
          onComplete(healthChecks.readyResult())(healthCheckResponse)
        }
      },
      path(PathMatchers.separateOnSlashes(settings.livenessPath)) {
        get {
          onComplete(healthChecks.aliveResult())(healthCheckResponse)
        }
      }
    )
  }
}
