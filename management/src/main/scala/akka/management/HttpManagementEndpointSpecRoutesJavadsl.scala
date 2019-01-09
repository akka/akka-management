/*
 * Copyright (C) 2017-2018 Lightbend Inc. <http://www.lightbend.com>
 */

package akka.management

import akka.http.javadsl.server.directives.RouteAdapter
import akka.http.scaladsl.server.Directives

class HttpManagementEndpointSpecRoutesJavadsl extends javadsl.ManagementRouteProvider with Directives {
  override def routes(settings: javadsl.ManagementRouteProviderSettings): akka.http.javadsl.server.Route =
    RouteAdapter {
      path("javadsl") {
        get {
          complete("hello Java")
        }
      }
    }
}
