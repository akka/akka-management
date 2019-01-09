package akka.management.http
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.{Matchers, WordSpec, WordSpecLike}
import akka.http.scaladsl.model.{StatusCodes, Uri}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.http.scaladsl.server._
import Directives._
import akka.actor.{ActorSystem, ExtendedActorSystem}
import akka.testkit.TestKit

import scala.concurrent.Future

class HealthCheckRoutesSpec
    extends WordSpec
    with Matchers
    with ScalatestRouteTest {

  val aes = system.asInstanceOf[ExtendedActorSystem]

  private def testRoute(
    readyResult: Future[Boolean] = Future.successful(true),
    aliveResult: Future[Boolean] = Future.successful(true)
  ): Route = {
    new HealthCheckRoutes(aes) {
      override protected val healthChecks: HealthChecks = new HealthChecks {
        override def ready(): Future[Boolean] = readyResult
        override def alive(): Future[Boolean] = aliveResult
      }
    }.routes(ManagementRouteProviderSettingsImpl(Uri("http://whocares")))
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
