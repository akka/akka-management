/*
 * Copyright (C) 2017-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.management.loglevels.logback

import akka.actor.ExtendedActorSystem
import akka.actor.Extension
import akka.actor.ExtensionId
import akka.annotation.InternalApi
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import akka.management.scaladsl.ManagementRouteProvider
import akka.management.scaladsl.ManagementRouteProviderSettings
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.unmarshalling.Unmarshaller
import ch.qos.logback.classic.Level
import ch.qos.logback.classic.LoggerContext
import org.slf4j.LoggerFactory
import akka.event.{ Logging => ClassicLogging }

object LogLevelRoutes extends ExtensionId[LogLevelRoutes] {
  override def createExtension(system: ExtendedActorSystem): LogLevelRoutes =
    new LogLevelRoutes(system)
}

/**
 * Provides the path loglevel/logger which can be used to dynamically change log levels
 *
 * INTERNAL API
 */
@InternalApi
final class LogLevelRoutes private (system: ExtendedActorSystem) extends Extension with ManagementRouteProvider {

  private val logger = LoggerFactory.getLogger(classOf[LogLevelRoutes])

  import LoggingUnmarshallers._

  private def getLogger(name: String) = {
    val context = LoggerFactory.getILoggerFactory.asInstanceOf[LoggerContext]
    context.getLogger(name)
  }

  override def routes(settings: ManagementRouteProviderSettings): Route =
    pathPrefix("loglevel") {
      concat(
        path("logback") {
          parameter("logger") {
            loggerName =>
              concat(
                put {
                  if (settings.readOnly) complete(StatusCodes.Forbidden)
                  else {
                    parameter("level".as[Level]) { level =>
                      extractClientIP { clientIp =>
                        val logger = getLogger(loggerName)
                        if (logger != null) {
                          logger.info(
                            "Log level for [{}] set to [{}] through Akka Management loglevel endpoint from [{}]",
                            loggerName,
                            level,
                            clientIp
                          )
                          logger.setLevel(level)
                          complete(StatusCodes.OK)
                        } else {
                          complete(StatusCodes.NotFound)
                        }
                      }
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
        },
        path("akka") {
          concat(
            get {
              complete(classicLogLevelName(system.eventStream.logLevel))
            },
            put {
              if (settings.readOnly) complete(StatusCodes.Forbidden)
              else {

                parameter("level".as[ClassicLogging.LogLevel]) { level =>
                  extractClientIP { clientIp =>
                    logger.info(
                      "Akka loglevel set to [{}] through Akka Management loglevel endpoint from [{}]",
                      Array[Object](classicLogLevelName(level), clientIp): _*
                    )
                    system.eventStream.setLogLevel(level)
                    complete(StatusCodes.OK)
                  }
                }
              }
            }
          )
        }
      )
    }
}

/**
 * INTERNAL API
 */
@InternalApi
private[akka] object LoggingUnmarshallers {
  private val validLevels =
    Set(Level.ALL, Level.DEBUG, Level.ERROR, Level.INFO, Level.OFF, Level.TRACE, Level.WARN).map(_.toString)

  implicit val levelFromStringUnmarshaller: Unmarshaller[String, Level] =
    Unmarshaller.strict { string =>
      if (!validLevels(string.toUpperCase))
        throw new IllegalArgumentException(s"Unknown logger level $string, allowed are [${validLevels.mkString(",")}]")
      Level.valueOf(string)
    }

  implicit val classicLevelFromStringUnmarshaller: Unmarshaller[String, ClassicLogging.LogLevel] =
    Unmarshaller.strict { string =>
      ClassicLogging
        .levelFor(string)
        .getOrElse(
          throw new IllegalArgumentException(
            s"Unknown logger level $string, allowed are [${ClassicLogging.AllLogLevels.map(_.toString).mkString(",")}]"
          )
        )
    }

  def classicLogLevelName(level: ClassicLogging.LogLevel): String = level match {
    case ClassicLogging.OffLevel     => "OFF"
    case ClassicLogging.DebugLevel   => "DEBUG"
    case ClassicLogging.InfoLevel    => "INFO"
    case ClassicLogging.WarningLevel => "WARNING"
    case ClassicLogging.ErrorLevel   => "ERROR"
    case _                           => s"Unknown loglevel: $level"
  }
}
