/*
 * Copyright (C) 2017 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.discovery.kubernetes

import scala.collection.immutable.Seq

object PodList {
  case class Port(name: String, containerPort: Int)
  case class Container(name: String, ports: Seq[Port])
  case class Spec(containers: Seq[Container])
  case class Status(podIP: Option[String])
  case class Item(spec: Spec, status: Status)
}

import PodList._

case class PodList(items: Seq[Item])
