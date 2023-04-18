/*
 * Copyright (C) 2017-2023 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.management.scaladsl

import akka.actor.Extension
import akka.annotation.InternalApi
import akka.http.scaladsl.server.Route
import akka.management.javadsl

/** Extend this trait in your extension in order to allow it to contribute routes to Akka Management starts its HTTP endpoint */
trait ManagementRouteProvider extends Extension {

  /** Routes to be exposed by Akka cluster management */
  def routes(settings: ManagementRouteProviderSettings): Route

}

/**
 * INTERNAL API
 */
@InternalApi private[akka] final class ManagementRouteProviderAdapter(delegate: javadsl.ManagementRouteProvider)
    extends ManagementRouteProvider {
  override def routes(settings: ManagementRouteProviderSettings): Route = {
    val settingsImpl = settings.asInstanceOf[ManagementRouteProviderSettingsImpl]
    delegate.routes(settingsImpl.asJava).asScala
  }

}
