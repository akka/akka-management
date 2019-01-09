/*
 * Copyright (C) 2017-2018 Lightbend Inc. <http://www.lightbend.com>
 */

package akka.management

import akka.http.scaladsl.server.Directives
import akka.http.scaladsl.server.Route
import akka.management.scaladsl.ManagementRouteProvider
import akka.management.scaladsl.ManagementRouteProviderSettings

class HttpManagementEndpointSpecRoutesScaladsl extends ManagementRouteProvider with Directives {
  override def routes(settings: ManagementRouteProviderSettings): Route =
    path("scaladsl") {
      get {
        complete("hello Scala")
      }
    }
}
