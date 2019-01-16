package akka.cluster.http.management
import akka.actor.ExtendedActorSystem
import akka.http.scaladsl.model.{StatusCodes, Uri}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.management.cluster.ClusterHttpManagementRouteProvider
import akka.management.scaladsl.ManagementRouteProviderSettings
import org.scalatest.{Matchers, WordSpec}
import akka.http.scaladsl.server._
import Directives._

object ClusterHttpManagementRouteProviderSpec {
  val config = ConfigFactory.parseString(
    """

    """)
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
