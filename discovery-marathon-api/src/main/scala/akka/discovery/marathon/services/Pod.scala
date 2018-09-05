/*
 * Copyright (C) 2017 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.discovery.marathon.services

final case class Pod(spec: Pod.Spec, instances: Seq[Pod.Instance]) extends MarathonService

object Pod {
  final case class Spec(labels: Option[Map[String, String]],
                        networks: Option[Seq[SpecNetwork]],
                        containers: Seq[Container]) {
    def networksSeq: Seq[SpecNetwork] = networks.getOrElse(Seq.empty)
  }

  final case class Instance(networks: Seq[InstanceNetwork], containers: Seq[Container])

  final case class SpecNetwork(mode: String)

  final case class InstanceNetwork(name: Option[String], addresses: Seq[String])

  final case class Container(endpoints: Option[Seq[Endpoint]]) {
    def endpointsSeq: Seq[Endpoint] = endpoints.getOrElse(Seq.empty)
  }

  final case class Endpoint(name: String,
                            allocatedHostPort: Option[Int],
                            hostPort: Option[Int],
                            containerPort: Option[Int],
                            networkNames: Option[Seq[String]]) {
    def networkNamesSeq: Seq[String] = networkNames.getOrElse(Seq.empty)
  }
}
