/*
 * Copyright (C) 2017 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.discovery.kubernetes

import scala.collection.immutable.Seq

object PodList {
  case class Metadata(deletionTimestamp: Option[String])
  case class ContainerPort(name: Option[String], containerPort: Int)
  case class Container(name: String, ports: Option[Seq[ContainerPort]])
  case class PodSpec(containers: Seq[Container])
  case class PodStatus(podIP: Option[String])
  case class Pod(spec: Option[PodSpec], status: Option[PodStatus], metadata: Option[Metadata])
}

import PodList._

case class PodList(items: Seq[Pod])
