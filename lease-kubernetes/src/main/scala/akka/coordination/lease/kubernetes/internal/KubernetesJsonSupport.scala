/*
 * Copyright (C) 2017-2023 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.coordination.lease.kubernetes.internal

import akka.annotation.InternalApi
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.{ DefaultJsonProtocol, JsonFormat, RootJsonFormat }

/**
 * INTERNAL API
 */
@InternalApi
case class LeaseCustomResource(
    metadata: Metadata,
    spec: Spec,
    kind: String = "Lease",
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
case class Spec(owner: String, time: Long)

/**
 * INTERNAL API
 */
@InternalApi
trait KubernetesJsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  // If adding more formats here, remember to also add in META-INF/native-image reflect config
  implicit val metadataFormat: JsonFormat[Metadata] = jsonFormat2(Metadata.apply)
  implicit val specFormat: JsonFormat[Spec] = jsonFormat2(Spec.apply)
  implicit val leaseCustomResourceFormat: RootJsonFormat[LeaseCustomResource] = jsonFormat4(LeaseCustomResource.apply)
}
