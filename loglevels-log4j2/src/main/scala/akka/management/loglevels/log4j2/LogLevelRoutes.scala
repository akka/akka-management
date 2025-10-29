/*
 * Copyright (C) 2017-2025 Lightbend Inc. <https://akka.io>
 */

package akka.management.loglevels.log4j2

import akka.actor.{ ExtendedActorSystem, Extension, ExtensionId }
import akka.annotation.InternalApi
import akka.event.{ Logging => ClassicLogging }
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.unmarshalling.Unmarshaller
import akka.management.scaladsl.{ ManagementRouteProvider, ManagementRouteProviderSettings }
import org.apache.logging.log4j.{ Level, LogManager }
import org.apache.logging.log4j.core.LoggerContext
import org.slf4j.LoggerFactory

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

  private val log = LoggerFactory.getLogger(classOf[LogLevelRoutes])

  import LoggingUnmarshallers._

  override def routes(settings: ManagementRouteProviderSettings): Route = {
    pathPrefix("loglevel") {
      extractClientIP { clientIp =>
        path("log4j2") {
          pathEndOrSingleSlash {
            put {
              parameters(
                "level".as[Level].withDefault(Level.INFO),
                "logger" ? LogManager.ROOT_LOGGER_NAME
              ) { (level, logger) =>
                if (settings.readOnly) {
                  complete(StatusCodes.Forbidden)
                } else {
                  log.info(
                    s"Log level for [${if (logger.equals(LogManager.ROOT_LOGGER_NAME)) "Root" else logger}] set to [$level] through Akka Management loglevel endpoint from [$clientIp]")
                  val context = LogManager.getContext(false).asInstanceOf[LoggerContext]
                  val config = context.getConfiguration
                  val loggerConfig = config.getLoggerConfig(logger)
                  loggerConfig.setLevel(level)
                  context.updateLoggers()
                  complete(StatusCodes.OK)
                }
              }
            } ~
            get {
              parameters(
                "logger" ? LogManager.ROOT_LOGGER_NAME
              ) { logger =>
                val context = LogManager.getContext(false).asInstanceOf[LoggerContext]
                val config = context.getConfiguration
                val loggerConfig = config.getLoggerConfig(logger)

                complete(loggerConfig.getLevel.toString)
              }
            }
          }
        } ~
        path("akka") {
          get {
            complete(classicLogLevelName(system.eventStream.logLevel))
          } ~
          put {
            if (settings.readOnly)
              complete(StatusCodes.Forbidden)
            else {
              parameter("level".as[ClassicLogging.LogLevel]) { level =>
                log.info(
                  "Akka loglevel set to [{}] through Akka Management loglevel endpoint from [{}]",
                  Array[Object](classicLogLevelName(level), clientIp): _*
                )
                system.eventStream.setLogLevel(level)
                complete(StatusCodes.OK)
              }
            }
          }
        }
      }
    }
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
