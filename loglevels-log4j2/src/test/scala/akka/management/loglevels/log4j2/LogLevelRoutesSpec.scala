/*
 * Copyright (C) 2017-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.management.loglevels.log4j2

import akka.actor.ExtendedActorSystem
import akka.event.{ Logging => ClassicLogging }
import akka.http.javadsl.server.MalformedQueryParamRejection
import akka.http.scaladsl.model.{ StatusCodes, Uri }
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.management.scaladsl.ManagementRouteProviderSettings
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.slf4j.LoggerFactory

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
      Get("/loglevel/log4j2?logger=LogLevelRoutesSpec") ~> routes ~> check {
        responseAs[String]
      }
    }

    "change log level of a Logger to ERROR" in {
      Put("/loglevel/log4j2?logger=LogLevelRoutesSpec&level=ERROR") ~> routes ~> check {
        response.status should ===(StatusCodes.OK)
        println(response)
        LoggerFactory.getLogger("LogLevelRoutesSpec").isErrorEnabled should ===(true)
      }
    }

    "change log level of a Logger to DEBUG" in {
      Put("/loglevel/log4j2?logger=LogLevelRoutesSpec&level=DEBUG") ~> routes ~> check {
        response.status should ===(StatusCodes.OK)
        println(response)
        LoggerFactory.getLogger("LogLevelRoutesSpec").isDebugEnabled() should ===(true)
      }
    }

    "change log level of a Logger to INFO" in {
      Put("/loglevel/log4j2?logger=LogLevelRoutesSpec&level=INFO") ~> routes ~> check {
        response.status should ===(StatusCodes.OK)
        println(response)
        LoggerFactory.getLogger("LogLevelRoutesSpec").isInfoEnabled should ===(true)
      }
    }

    "change log level of a Logger to WARN" in {
      Put("/loglevel/log4j2?logger=LogLevelRoutesSpec&level=WARN") ~> routes ~> check {
        response.status should ===(StatusCodes.OK)
        println(response)
        LoggerFactory.getLogger("LogLevelRoutesSpec").isWarnEnabled should ===(true)
      }
    }

    "fail for unknown log level" in {
      Put("/loglevel/log4j2?logger=LogLevelRoutesSpec&level=MONKEY") ~> routes ~> check {
        rejection shouldBe an[MalformedQueryParamRejection]
      }
    }

    "not change loglevel if read only" in {
      val readOnlyRoutes = LogLevelRoutes
        .createExtension(system.asInstanceOf[ExtendedActorSystem])
        .routes(ManagementRouteProviderSettings(Uri("https://example.com"), readOnly = true))
      Put("/loglevel/log4j2?logger=LogLevelRoutesSpec&level=DEBUG") ~> readOnlyRoutes ~> check {
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
