/*
 * Copyright (C) 2017-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.management.javadsl

import java.util.concurrent.CompletionStage
import java.util.function.{ Function => JFunction }

import scala.compat.java8.FutureConverters._
import akka.Done
import akka.actor.{ ActorSystem, ClassicActorSystemProvider }
import akka.http.javadsl.model.Uri
import akka.http.javadsl.server.directives.RouteAdapter
import akka.management.AkkaManagementSettings
import akka.management.scaladsl

object AkkaManagement {
  def get(system: ActorSystem): AkkaManagement =
    new AkkaManagement(scaladsl.AkkaManagement(system))

  def get(classicActorSystemProvider: ClassicActorSystemProvider): AkkaManagement =
    new AkkaManagement(scaladsl.AkkaManagement(classicActorSystemProvider))
}

final class AkkaManagement(delegate: scaladsl.AkkaManagement) {

  def settings: AkkaManagementSettings = delegate.settings

  /**
   * Get the routes for the HTTP management endpoint.
   *
   * This method can be used to embed the Akka management routes in an existing Akka HTTP server.
   * @throws java.lang.IllegalArgumentException if routes not configured for akka management
   */
  def getRoutes: akka.http.javadsl.server.Route =
    RouteAdapter(delegate.routes)

  /**
   * Amend the [[ManagementRouteProviderSettings]] and get the routes for the HTTP management endpoint.
   *
   * Use this when adding authentication and HTTPS.
   *
   * This method can be used to embed the Akka management routes in an existing Akka HTTP server.
   * @throws java.lang.IllegalArgumentException if routes not configured for akka management
   */
  def getRoutes(transformSettings: JFunction[ManagementRouteProviderSettings, ManagementRouteProviderSettings])
      : akka.http.javadsl.server.Route =
    RouteAdapter(delegate.routes(convertSettingsTransformation(transformSettings)))

  private def convertSettingsTransformation(
      transformSettings: JFunction[ManagementRouteProviderSettings, ManagementRouteProviderSettings])
      : scaladsl.ManagementRouteProviderSettings => scaladsl.ManagementRouteProviderSettings = { scaladslSettings =>
    {
      val scaladslSettingsImpl = scaladslSettings.asInstanceOf[scaladsl.ManagementRouteProviderSettingsImpl]
      val javadslTransformedSettings = transformSettings.apply(scaladslSettingsImpl.asJava)
      val javadslTransformedSettingsImpl = javadslTransformedSettings.asInstanceOf[ManagementRouteProviderSettingsImpl]
      javadslTransformedSettingsImpl.asScala
    }
  }

  /**
   * Start an Akka HTTP server to serve the HTTP management endpoint.
   */
  def start(): CompletionStage[Uri] =
    delegate.start().map(Uri.create)(delegate.system.dispatcher).toJava

  /**
   * Start an Akka HTTP server to serve the HTTP management endpoint.
   */
  def start(transformSettings: JFunction[ManagementRouteProviderSettings, ManagementRouteProviderSettings])
      : CompletionStage[Uri] =
    delegate.start(convertSettingsTransformation(transformSettings)).map(Uri.create)(delegate.system.dispatcher).toJava

  def stop(): CompletionStage[Done] =
    delegate.stop().toJava

}
