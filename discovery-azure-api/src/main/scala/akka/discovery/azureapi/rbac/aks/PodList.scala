/*
 * Copyright (C) 2017-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.discovery.azureapi.rbac.aks

import akka.annotation.InternalApi

import scala.collection.immutable

/**
 * INTERNAL API
 */
@InternalApi
private[aks] object PodList {
  final case class Metadata(deletionTimestamp: Option[String], labels: Map[String, String] = Map.empty)

  final case class ContainerPort(name: Option[String], containerPort: Int)

  final case class Container(name: String, ports: Option[immutable.Seq[ContainerPort]])

  final case class PodSpec(containers: immutable.Seq[Container])

  final case class ContainerStatus(name: String, state: Map[String, Unit])

  final case class PodStatus(
      podIP: Option[String],
      containerStatuses: Option[immutable.Seq[ContainerStatus]],
      phase: Option[String])

  final case class Pod(spec: Option[PodSpec], status: Option[PodStatus], metadata: Option[Metadata])
}

/**
 * INTERNAL API
 */
@InternalApi
private[aks] final case class PodList(items: immutable.Seq[PodList.Pod])
