/*
 * Copyright (C) 2017-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.management.cluster

import akka.actor.ExtendedActorSystem
import akka.cluster.Cluster
import akka.http.scaladsl.model.{ StatusCodes, Uri }
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.management.scaladsl.ManagementRouteProviderSettings
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

object ClusterHttpManagementRouteProviderSpec {}

class ClusterHttpManagementRouteProviderSpec extends AnyWordSpec with ScalatestRouteTest with Matchers {

  val cluster = Cluster(system)

  "Cluster HTTP Management Route" should {
    val routes = ClusterHttpManagementRouteProvider(
      system.asInstanceOf[ExtendedActorSystem]
    )
    "not expose write operations when readOnly set" in {
      val readOnlyRoutes = routes.routes(
        ManagementRouteProviderSettings(
          Uri("http://localhost"),
          readOnly = true
        )
      )
      Get("/cluster/members") ~> readOnlyRoutes ~> check {
        handled shouldEqual true
        status shouldEqual StatusCodes.OK
      }
      Post("/cluster/members") ~> readOnlyRoutes ~> check {
        status shouldEqual StatusCodes.MethodNotAllowed
      }
      Get("/cluster/members/member1") ~> readOnlyRoutes ~> check {
        handled shouldEqual true
        status shouldEqual StatusCodes.NotFound
      }
      Delete("/cluster/members/member1") ~> readOnlyRoutes ~> check {
        status shouldEqual StatusCodes.MethodNotAllowed
      }
      Put("/cluster/members/member1") ~> readOnlyRoutes ~> check {
        status shouldEqual StatusCodes.MethodNotAllowed
      }
    }

    "expose write when readOnly false" in {
      val allRoutes = routes.routes(
        ManagementRouteProviderSettings(
          Uri("http://localhost"),
          readOnly = false
        )
      )
      Get("/cluster/members") ~> allRoutes ~> check {
        handled shouldEqual true
      }
      Get("/cluster/members/member1") ~> allRoutes ~> check {
        handled shouldEqual true
        status shouldEqual StatusCodes.NotFound
      }
      Delete("/cluster/members/member1") ~> allRoutes ~> check {
        handled shouldEqual true
        status shouldEqual StatusCodes.NotFound
      }
      Put("/cluster/members/member1") ~> allRoutes ~> check {
        handled shouldEqual true
        status shouldEqual StatusCodes.NotFound
      }
    }
  }

}
