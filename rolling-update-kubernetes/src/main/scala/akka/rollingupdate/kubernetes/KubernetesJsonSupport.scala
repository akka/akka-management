/*
 * Copyright (C) 2017-2023 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.rollingupdate.kubernetes

import scala.collection.immutable

import akka.annotation.InternalApi
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.DefaultJsonProtocol
import spray.json.JsonFormat
import spray.json.RootJsonFormat

/**
 * INTERNAL API
 */
@InternalApi
case class PodCostCustomResource(
    metadata: Metadata,
    spec: Spec,
    kind: String = "PodCost",
    apiVersion: String = "akka.io/v1")

/**
 * INTERNAL API
 */
@InternalApi
case class Metadata(name: String, resourceVersion: Option[String])

/**
 * INTERNAL API
 */
@InternalApi
case class Spec(pods: immutable.Seq[PodCost])

/**
 * INTERNAL API
 */
@InternalApi
trait KubernetesJsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val metadataFormat: JsonFormat[Metadata] = jsonFormat2(Metadata.apply)
  implicit val podCostFormat: JsonFormat[PodCost] = jsonFormat5(PodCost.apply)
  implicit val specFormat: JsonFormat[Spec] = jsonFormat1(Spec.apply)
  implicit val podCostCustomResourceFormat: RootJsonFormat[PodCostCustomResource] = jsonFormat4(
    PodCostCustomResource.apply)
}
