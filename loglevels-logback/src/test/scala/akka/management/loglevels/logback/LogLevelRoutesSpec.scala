/*
 * Copyright (C) 2017-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.management.loglevels.logback

import akka.actor.ExtendedActorSystem
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.Matchers
import org.scalatest.WordSpec
import org.slf4j.LoggerFactory

class LogLevelRoutesSpec extends WordSpec with Matchers with ScalatestRouteTest {

  val routes = LogLevelRoutes.createExtension(system.asInstanceOf[ExtendedActorSystem]).routes(null)

  "The logback log level routes" must {

    "show log level of a logger" in {
      Get("/loglevel?logger=LogLevelRoutesSpec") ~> routes ~> check {
        responseAs[String]
      }

    }

    "change log level of a router" in {
      Post("/loglevel?logger=LogLevelRoutesSpec&level=DEBUG") ~> routes ~> check {
        response.status should === (StatusCodes.OK)
        LoggerFactory.getLogger("LogLevelRoutesSpec").isDebugEnabled should === (true)
      }
    }

  }

}
