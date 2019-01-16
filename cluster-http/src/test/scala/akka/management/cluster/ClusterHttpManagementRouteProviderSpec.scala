package akka.management.cluster

import akka.actor.ExtendedActorSystem
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._


object ClusterHttpManagementRouteProviderSpec {

}

class ClusterHttpManagementRouteProviderSpec extends WordSpec with ScalatestRouteTest with Matchers {
  "Cluster HTTP Management Route" should {
    val routes = ClusterHttpManagementRouteProvider(system.asInstanceOf[ExtendedActorSystem])
    "not expose write operations when readOnly set" in {
      val readOnlyRoutes: Route = routes.routes(ManagementRouteProviderSettings(Uri("http://localhost"), readOnly = true))

      Get("cluster" / "members") ~> readOnlyRoutes ~> check {
        status shouldBe StatusCodes.OK
      }

    }
    "expose write when readOnly false" in {

    }
  }

}
