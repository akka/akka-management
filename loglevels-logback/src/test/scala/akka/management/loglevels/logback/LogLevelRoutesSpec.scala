/*
 * Copyright (C) 2017-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.management.loglevels.logback

import akka.actor.ExtendedActorSystem
import akka.http.javadsl.server.MalformedQueryParamRejection
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.management.scaladsl.ManagementRouteProviderSettings
import org.scalatest.Matchers
import org.scalatest.WordSpec
import org.slf4j.LoggerFactory

class LogLevelRoutesSpec extends WordSpec with Matchers with ScalatestRouteTest {

  val routes = LogLevelRoutes
    .createExtension(system.asInstanceOf[ExtendedActorSystem])
    .routes(ManagementRouteProviderSettings(Uri("https://example.com"), readOnly = false))

  "The logback log level routes" must {

    "show log level of a logger" in {
      Get("/loglevel?logger=LogLevelRoutesSpec") ~> routes ~> check {
        responseAs[String]
      }
    }

    "change log level of a router" in {
      Post("/loglevel?logger=LogLevelRoutesSpec&level=DEBUG") ~> routes ~> check {
        response.status should ===(StatusCodes.OK)
        LoggerFactory.getLogger("LogLevelRoutesSpec").isDebugEnabled should ===(true)
      }
    }

    "fail for unknown log level" in {
      Post("/loglevel?logger=LogLevelRoutesSpec&level=MONKEY") ~> routes ~> check {
        rejection shouldBe an[MalformedQueryParamRejection]
      }
    }

    "not change loglevel if read only" in {
      val readOnlyRoutes = LogLevelRoutes
        .createExtension(system.asInstanceOf[ExtendedActorSystem])
        .routes(ManagementRouteProviderSettings(Uri("https://example.com"), readOnly = true))
      Post("/loglevel?logger=LogLevelRoutesSpec&level=DEBUG") ~> readOnlyRoutes ~> check {
        response.status should ===(StatusCodes.Forbidden)
      }
    }
  }

}
