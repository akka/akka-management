/*
 * Copyright (C) 2017-2025 Lightbend Inc. <https://akka.io>
 */

package akka.discovery.marathon

import scala.collection.immutable.Seq

@deprecated("Use another discovery method instead.", "1.6.0")
object AppList {
  case class App(container: Option[Container], portDefinitions: Option[Seq[PortDefinition]], tasks: Option[Seq[Task]])
  case class Container(portMappings: Option[Seq[PortMapping]], docker: Option[Docker])
  case class Docker(portMappings: Option[Seq[PortMapping]])
  case class Task(host: Option[String], ports: Option[Seq[Int]])
  case class PortDefinition(name: Option[String], port: Option[Int])
  case class PortMapping(servicePort: Option[Int], name: Option[String])
}

import AppList._

@deprecated("Use another discovery method instead.", "1.6.0")
case class AppList(apps: Seq[App])
