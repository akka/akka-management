/*
 * Copyright (C) 2017-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.management.loglevels.logback

import akka.actor.ExtendedActorSystem
import akka.http.javadsl.server.MalformedQueryParamRejection
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.management.scaladsl.ManagementRouteProviderSettings
import org.slf4j.LoggerFactory
import akka.event.{ Logging => ClassicLogging }
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class LogLevelRoutesSpec extends AnyWordSpec with Matchers with ScalatestRouteTest {

  override def testConfigSource: String =
    """
      akka.loglevel = INFO
      """

  val routes = LogLevelRoutes
    .createExtension(system.asInstanceOf[ExtendedActorSystem])
    .routes(ManagementRouteProviderSettings(Uri("https://example.com"), readOnly = false))

  "The logback log level routes" must {

    "show log level of a Logger" in {
      Get("/loglevel/logback?logger=LogLevelRoutesSpec") ~> routes ~> check {
        responseAs[String]
      }
    }

    "change log level of a Logger" in {
      Put("/loglevel/logback?logger=LogLevelRoutesSpec&level=DEBUG") ~> routes ~> check {
        response.status should ===(StatusCodes.OK)
        LoggerFactory.getLogger("LogLevelRoutesSpec").isDebugEnabled should ===(true)
      }
    }

    "fail for unknown log level" in {
      Put("/loglevel/logback?logger=LogLevelRoutesSpec&level=MONKEY") ~> routes ~> check {
        rejection shouldBe an[MalformedQueryParamRejection]
      }
    }

    "not change loglevel if read only" in {
      val readOnlyRoutes = LogLevelRoutes
        .createExtension(system.asInstanceOf[ExtendedActorSystem])
        .routes(ManagementRouteProviderSettings(Uri("https://example.com"), readOnly = true))
      Put("/loglevel/logback?logger=LogLevelRoutesSpec&level=DEBUG") ~> readOnlyRoutes ~> check {
        response.status should ===(StatusCodes.Forbidden)
      }
    }

    "allow inspecting classic Akka loglevel" in {
      Get("/loglevel/akka") ~> routes ~> check {
        response.status should ===(StatusCodes.OK)
        responseAs[String] should ===("INFO")
      }
    }

    "allow changing classic Akka loglevel" in {
      Put("/loglevel/akka?level=DEBUG") ~> routes ~> check {
        response.status should ===(StatusCodes.OK)
        system.eventStream.logLevel should ===(ClassicLogging.DebugLevel)
      }
    }
  }

}
