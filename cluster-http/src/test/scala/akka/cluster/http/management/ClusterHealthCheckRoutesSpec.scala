/*
 * Copyright (C) 2017-2018 Lightbend Inc. <http://www.lightbend.com>
 */

package akka.cluster.http.management

import akka.cluster.MemberStatus
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.management.cluster.ClusterHealthCheckRoutes
import akka.management.cluster.ClusterHealthCheckRoutes.ClusterHealthCheckSettings
import com.typesafe.config.ConfigFactory
import org.scalatest.{ Matchers, WordSpec }

class ClusterHealthCheckRoutesSpec extends WordSpec with Matchers with ScalatestRouteTest {

  def withRoute(status: MemberStatus)(f: Route => Unit): Unit = {
    val underTest = new ClusterHealthCheckRoutes(
      new ClusterHealthCheckSettings(
        ConfigFactory.parseString("""
      ready-states = ["Up"] 
    """)
      ),
      () => status
    )

    f(underTest.routes)
  }

  "Cluster HTTP health check" must {

    "return 200 for a ready state" in {
      withRoute(MemberStatus.Up) { route =>
        Get("/ready") ~> route ~> check {
          status shouldEqual StatusCodes.OK
        }
      }

    }

    "return 500 for unready state" in {
      withRoute(MemberStatus.WeaklyUp) { route =>
        Get("/ready") ~> route ~> check {
          status shouldEqual StatusCodes.InternalServerError
        }
      }
      withRoute(MemberStatus.Down) { route =>
        Get("/ready") ~> route ~> check {
          status shouldEqual StatusCodes.InternalServerError
        }
      }
    }
  }

  "Cluster HTTP liveness check" must {

    "always return 200" in {
      withRoute(MemberStatus.Down) { route =>
        Get("/alive") ~> route ~> check {
          status shouldEqual StatusCodes.OK
        }
      }
    }
  }
}
