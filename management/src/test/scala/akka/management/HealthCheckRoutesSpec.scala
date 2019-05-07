/*
 * Copyright (C) 2017-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.management

import akka.actor.ExtendedActorSystem
import akka.http.scaladsl.model.{ StatusCodes, Uri }
import akka.http.scaladsl.server._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.management.scaladsl.{ HealthChecks, ManagementRouteProviderSettings }
import org.scalatest.{ Matchers, WordSpec }

import scala.concurrent.Future

class HealthCheckRoutesSpec extends WordSpec with Matchers with ScalatestRouteTest {

  private val eas = system.asInstanceOf[ExtendedActorSystem]

  private def testRoute(
      readyResult: Future[Boolean] = Future.successful(true),
      aliveResult: Future[Boolean] = Future.successful(true)
  ): Route = {
    new HealthCheckRoutes(eas) {
      override protected val healthChecks: HealthChecks = new HealthChecks {
        override def ready(): Future[Boolean] = readyResult
        override def alive(): Future[Boolean] = aliveResult
      }
    }.routes(ManagementRouteProviderSettings(Uri("http://whocares"), readOnly = false))
  }

  tests("/ready", result => testRoute(readyResult = result))
  tests("/alive", result => testRoute(aliveResult = result))

  def tests(endpoint: String, route: Future[Boolean] => Route) = {
    s"Health check ${endpoint} endpoint" should {
      "return 200 for true" in {
        Get(endpoint) ~> route(Future.successful(true)) ~> check {
          status shouldEqual StatusCodes.OK
        }
      }
      "return 500 for false" in {
        Get(endpoint) ~> route(Future.successful(false)) ~> check {
          status shouldEqual StatusCodes.InternalServerError
          responseAs[String] shouldEqual "Not Healthy"
        }
      }
      "return 500 for fail" in {
        Get(endpoint) ~> route(Future.failed(new RuntimeException("darn it"))) ~> check {
          status shouldEqual StatusCodes.InternalServerError
          responseAs[String] shouldEqual "Health Check Failed: darn it"
        }
      }
    }
  }
}
