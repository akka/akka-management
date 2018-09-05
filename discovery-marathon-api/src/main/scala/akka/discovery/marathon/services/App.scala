/*
 * Copyright (C) 2017 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.discovery.marathon.services

final case class App(labels: Map[String, String],
                     networks: Seq[App.Network],
                     container: Option[App.Container],
                     portDefinitions: Option[Seq[App.PortDefinition]],
                     tasks: Seq[App.Task])
    extends MarathonService {
  def portDefinitionsSeq: Seq[App.PortDefinition] =
    portDefinitions.getOrElse(Seq.empty)
}

object App {
  final case class Network(mode: String)

  final case class Container(portMappings: Option[Seq[PortMapping]]) {
    def portMappingsSeq: Seq[PortMapping] = portMappings.getOrElse(Seq.empty)
  }

  final case class Task(ports: Seq[Int], ipAddresses: Seq[IpAddress])

  final case class IpAddress(ipAddress: String)

  final case class PortDefinition(name: Option[String])

  final case class PortMapping(containerPort: Int, name: Option[String])
}
