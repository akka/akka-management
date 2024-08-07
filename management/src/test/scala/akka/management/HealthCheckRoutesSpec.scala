/*
 * Copyright (C) 2017-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.management

import akka.actor.ExtendedActorSystem
import akka.http.scaladsl.model.{ StatusCodes, Uri }
import akka.http.scaladsl.server._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.management.scaladsl.{ HealthChecks, ManagementRouteProviderSettings }

import scala.concurrent.Future
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class HealthCheckRoutesSpec extends AnyWordSpec with Matchers with ScalatestRouteTest {

  private val eas = system.asInstanceOf[ExtendedActorSystem]

  private def testRoute(
      readyResultValue: Future[Either[String, Unit]] = Future.successful(Right(())),
      aliveResultValue: Future[Either[String, Unit]] = Future.successful(Right(()))
  ): Route = {
    new HealthCheckRoutes(eas) {
      override protected val healthChecks: HealthChecks = new HealthChecks {
        override def readyResult(): Future[Either[String, Unit]] = readyResultValue
        override def ready(): Future[Boolean] = readyResultValue.map(_.isRight)
        override def aliveResult(): Future[Either[String, Unit]] = aliveResultValue
        override def alive(): Future[Boolean] = aliveResultValue.map(_.isRight)
      }
    }.routes(ManagementRouteProviderSettings(Uri("http://whocares"), readOnly = false))
  }

  tests("/ready", result => testRoute(readyResultValue = result))
  tests("/alive", result => testRoute(aliveResultValue = result))

  def tests(endpoint: String, route: Future[Either[String, Unit]] => Route) = {
    s"Health check ${endpoint} endpoint" should {
      "return 200 for Right" in {
        Get(endpoint) ~> route(Future.successful(Right(()))) ~> check {
          status shouldEqual StatusCodes.OK
        }
      }
      "return 500 for Left" in {
        Get(endpoint) ~> route(Future.successful(Left("com.someclass.MyCheck"))) ~> check {
          status shouldEqual StatusCodes.InternalServerError
          responseAs[String] shouldEqual "Not Healthy: com.someclass.MyCheck"
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
