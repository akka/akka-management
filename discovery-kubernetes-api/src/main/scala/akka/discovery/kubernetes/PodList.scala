/*
 * Copyright (C) 2017-2025 Lightbend Inc. <https://akka.io>
 */

package akka.discovery.kubernetes

import scala.collection.immutable
import akka.annotation.InternalApi

/**
 * INTERNAL API
 */
@InternalApi private[kubernetes] object PodList {
  final case class Metadata(deletionTimestamp: Option[String])

  final case class ContainerPort(name: Option[String], containerPort: Int)

  final case class Container(name: String, ports: Option[immutable.Seq[ContainerPort]])

  final case class PodSpec(containers: immutable.Seq[Container])

  final case class ContainerStatus(name: String, state: Map[String, Unit])

  final case class PodCondition(`type`: Option[String], status: Option[String]) {
    def isAbleToServeRequests: Boolean = `type`.contains("Ready") && status.contains("True")
  }

  final case class PodStatus(
      podIP: Option[String],
      containerStatuses: Option[immutable.Seq[ContainerStatus]],
      phase: Option[String],
      conditions: Option[immutable.Seq[PodCondition]])

  final case class Pod(spec: Option[PodSpec], status: Option[PodStatus], metadata: Option[Metadata])
}

/**
 * INTERNAL API
 */
@InternalApi private[kubernetes] final case class PodList(items: immutable.Seq[PodList.Pod])
