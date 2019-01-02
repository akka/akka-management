/*
 * Copyright (C) 2017-2018 Lightbend Inc. <http://www.lightbend.com>
 */

package akka.discovery.kubernetes

import java.util.Optional

import scala.compat.java8.OptionConverters._
import scala.collection.immutable
import scala.collection.JavaConverters._

object PodList {
  final case class Metadata(deletionTimestamp: Option[String]) {
    def getDeletionTimestamp: Optional[String] = deletionTimestamp.asJava
  }

  final case class ContainerPort(name: Option[String], containerPort: Int) {
    def getName: Optional[String] = name.asJava
  }

  final case class Container(name: String, ports: Option[immutable.Seq[ContainerPort]]) {
    def getPorts: Optional[java.util.List[ContainerPort]] =
      ports.map(_.asJava).asJava
  }

  final case class PodSpec(containers: immutable.Seq[Container]) {
    def getContainers: java.util.List[Container] = containers.asJava
  }

  final case class PodStatus(podIP: Option[String]) {
    def getPodIP: Optional[String] = podIP.asJava
  }

  final case class Pod(spec: Option[PodSpec], status: Option[PodStatus], metadata: Option[Metadata]) {
    def getSpec: Optional[PodSpec] = spec.asJava
    def getStatus: Optional[PodStatus] = status.asJava
    def getMetadata: Optional[Metadata] = metadata.asJava
  }
}

final case class PodList(items: immutable.Seq[PodList.Pod]) {
  def getItems: java.util.List[PodList.Pod] = items.asJava
}
