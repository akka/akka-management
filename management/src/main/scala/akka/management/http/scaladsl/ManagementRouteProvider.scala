/*
 * Copyright (C) 2017-2018 Lightbend Inc. <http://www.lightbend.com>
 */

package akka.management.http.scaladsl

import akka.actor.Extension
import akka.annotation.InternalApi
import akka.http.scaladsl.server.Route
import akka.management.http.ManagementRouteProviderSettings
import akka.management.http.javadsl.{ ManagementRouteProvider => JManagementRouteProvider }

/** Extend this trait in your extension in order to allow it to contribute routes to Akka Management starts its HTTP endpoint */
trait ManagementRouteProvider extends Extension {

  /** Routes to be exposed by Akka cluster management */
  def routes(settings: ManagementRouteProviderSettings): Route

}

/**
 * INTERNAL API
 */
@InternalApi private[akka] final class ManagementRouteProviderAdapter(delegate: JManagementRouteProvider)
    extends ManagementRouteProvider {
  override def routes(settings: ManagementRouteProviderSettings): Route =
    delegate.routes(settings).asScala

}
