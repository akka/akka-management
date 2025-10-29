/*
 * Copyright (C) 2017-2025 Lightbend Inc. <https://akka.io>
 */

package akka.management.javadsl

import akka.actor.Extension
import akka.http.javadsl.server.Route

/** Extend this trait in your extension in order to allow it to contribute routes to Akka Management starts its HTTP endpoint */
trait ManagementRouteProvider extends Extension {

  /** Routes to be exposed by Akka cluster management */
  def routes(settings: ManagementRouteProviderSettings): Route

}
