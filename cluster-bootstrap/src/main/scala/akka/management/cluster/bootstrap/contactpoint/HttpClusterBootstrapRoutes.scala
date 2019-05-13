/*
 * Copyright (C) 2017-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.management.cluster.bootstrap.contactpoint

import scala.concurrent.duration._
import akka.actor.ActorSystem
import akka.event.Logging
import akka.event.LoggingAdapter
import akka.http.javadsl.server.directives.RouteAdapter
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.server.Route
import akka.management.cluster.bootstrap.ClusterBootstrapSettings
import akka.management.cluster.bootstrap.internal.ContactPoint

final class HttpClusterBootstrapRoutes(settings: ClusterBootstrapSettings) extends HttpBootstrapJsonProtocol {

  import akka.http.scaladsl.server.Directives._

  private def routeGetSeedNodes: Route = extractClientIP { clientIp ⇒
    extractActorSystem { implicit system ⇒
      val contactPoint = new ContactPoint(system, settings, log)
      complete(contactPoint.seedNodes(clientIp.toString))
    }
  }

  /** Scala API */
  val routes: Route =
    (get & path("bootstrap" / "seed-nodes")) {
      toStrictEntity(1.second) { // always drain everything
        routeGetSeedNodes
      }
    }

  /** Java API */
  def getRoutes: akka.http.javadsl.server.Route = RouteAdapter(routes)

  private def log(implicit sys: ActorSystem): LoggingAdapter =
    Logging(sys, classOf[HttpClusterBootstrapRoutes])

}

object ClusterBootstrapRequests {

  import akka.http.scaladsl.client.RequestBuilding._

  def bootstrapSeedNodes(baseUri: Uri): HttpRequest =
    Get(baseUri + "/bootstrap/seed-nodes")

}
