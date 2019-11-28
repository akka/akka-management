/*
 * Copyright (C) 2017-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.management.loglevels.logback

import akka.actor.ExtendedActorSystem
import akka.actor.Extension
import akka.actor.ExtensionId
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import akka.management.scaladsl.ManagementRouteProvider
import akka.management.scaladsl.ManagementRouteProviderSettings
import akka.http.scaladsl.server.Directives._
import ch.qos.logback.classic.Level
import ch.qos.logback.classic.LoggerContext
import org.slf4j.LoggerFactory

object LogLevelRoutes extends ExtensionId[LogLevelRoutes] {
  override def createExtension(system: ExtendedActorSystem): LogLevelRoutes =
    new LogLevelRoutes
}

/**
 * Provides the path loglevel/logger which can be used to dynamically change log levels
 */
class LogLevelRoutes private () extends Extension with ManagementRouteProvider {

  private def getLogger(name: String) = {
    val context = LoggerFactory.getILoggerFactory.asInstanceOf[LoggerContext]
    context.getLogger(name)
  }
  override def routes(settings: ManagementRouteProviderSettings): Route =
    path("loglevel") {
      parameter("logger") { loggerName =>
        concat(
          post {
            parameter("level") { level =>
              // FIXME proper unmarshall of Level
              val logger = getLogger(loggerName)

              if (logger != null) {
                logger.setLevel(Level.valueOf(level))
                complete(StatusCodes.OK)
              } else {
                complete(StatusCodes.NotFound)
              }
            }
          },
          get {
            val logger = getLogger(loggerName)

            if (logger != null) {
              complete(logger.getEffectiveLevel.toString)
            } else {
              complete(StatusCodes.NotFound)
            }
          }
        )
      }
    }

}
