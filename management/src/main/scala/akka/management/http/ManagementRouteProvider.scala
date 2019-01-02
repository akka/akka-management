/*
 * Copyright (C) 2017-2018 Lightbend Inc. <http://www.lightbend.com>
 */

package akka.management.http

import akka.actor.Extension
import akka.annotation.InternalApi
import akka.http.scaladsl.model.Uri

/** Extend this trait in your extension in order to allow it to contribute routes to Akka Management starts its HTTP endpoint */
trait ManagementRouteProvider extends Extension {

  /** Routes to be exposed by Akka cluster management */
  def routes(settings: ManagementRouteProviderSettings): akka.http.scaladsl.server.Route

  // FIXME API Java API, do we need two different ManagementRouteProvider for Scala implementations vs Java implementations?

}

/**
 * Settings object used to pass through information about the environment the routes will be running in,
 * from the component starting the actual HTTP server, to the [[ManagementRouteProvider]].
 */
trait ManagementRouteProviderSettings {

  /**
   * The "self" base Uri which points to the root of the HTTP server running the route provided by the Provider.
   * Can be used to introduce some self-awareness and/or links to "self" in the routes created by the Provider.
   */
  def selfBaseUri: Uri
}

/**
 * INTERNAL API
 */
@InternalApi private[akka] final case class ManagementRouteProviderSettingsImpl(
    override val selfBaseUri: Uri
) extends ManagementRouteProviderSettings
