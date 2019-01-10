/*
 * Copyright (C) 2017-2018 Lightbend Inc. <http://www.lightbend.com>
 */

package akka.management.javadsl

import java.util.concurrent.CompletionStage

import scala.compat.java8.FutureConverters._

import akka.Done
import akka.actor.ActorSystem
import akka.http.javadsl.model.Uri
import akka.http.javadsl.server.directives.RouteAdapter
import akka.management.scaladsl

object AkkaManagement {
  def get(system: ActorSystem): AkkaManagement =
    new AkkaManagement(scaladsl.AkkaManagement(system))
}

final class AkkaManagement(delegate: scaladsl.AkkaManagement) {

  /**
   * Get the routes for the HTTP management endpoint.
   *
   * This method can be used to embed the Akka management routes in an existing Akka HTTP server.
   * @throws IllegalArgumentException if routes not configured for akka management
   */
  def getRoutes: akka.http.javadsl.server.Route =
    RouteAdapter(delegate.routes)

  /**
   * Start an Akka HTTP server to serve the HTTP management endpoint.
   */
  def start(): CompletionStage[Uri] =
    delegate.start().map(Uri.create)(delegate.system.dispatcher).toJava

  def stop(): CompletionStage[Done] =
    delegate.stop().toJava

}
