package akka.management.typed.scaladsl

import akka.http.scaladsl.server.Directives
import akka.http.scaladsl.server.Route
import akka.actor.typed.{ActorSystem, Behavior}
import akka.actor.typed.scaladsl.adapter._
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpEntity, HttpRequest, HttpResponse, Uri}
import akka.management.scaladsl.{ManagementRouteProvider, ManagementRouteProviderSettings}
import com.typesafe.config.ConfigFactory

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext

object CompileOnly {
  def basicEndToEndDemo(): Unit = {
    val system = {
      val config = ConfigFactory parseString s"""
         |akka.management.http.route-providers = [ "${classOf[Foo].getName}" ]
       """.stripMargin
      ActorSystem[Nothing](Behavior.empty, "demo", config)
    }

    val http = Http(system.toUntyped)

    import ExecutionContext.Implicits.global
    val program = for {
      uri <- AkkaManagement(system).start()
      ping = HttpRequest(uri = uri.withPath(Uri.Path("/ping")))
      HttpResponse(_, _, HttpEntity.Strict(_, data), _) <- http.singleRequest(ping)
      _ <- http.shutdownAllConnectionPools()
      _ <- AkkaManagement(system).stop()
      _ <- system.terminate()
    } yield data.utf8String

    require {
      Await.result(program, 10.seconds) == "pong"
    }
  }

  class Foo extends ManagementRouteProvider with Directives {
    override def routes(settings: ManagementRouteProviderSettings): Route =
      path("ping") { complete("pong") }
  }
}
