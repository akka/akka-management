/*
 * Copyright (C) 2017-2023 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.discovery.kubernetes

import akka.annotation.InternalApi
import akka.discovery.kubernetes.PodList._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json._

/**
 * INTERNAL API
 */
@InternalApi private[akka] object JsonFormat extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val containerPortFormat: JsonFormat[ContainerPort] = jsonFormat2(ContainerPort.apply)
  implicit val containerFormat: JsonFormat[Container] = jsonFormat2(Container.apply)
  implicit val podSpecFormat: JsonFormat[PodSpec] = jsonFormat1(PodSpec.apply)
  implicit val containerStatusFormat: JsonFormat[ContainerStatus] = jsonFormat2(ContainerStatus.apply)
  implicit val podStatusFormat: JsonFormat[PodStatus] = jsonFormat3(PodStatus.apply)
  implicit val metadataFormat: JsonFormat[Metadata] = jsonFormat1(Metadata.apply)
  implicit val podFormat: JsonFormat[Pod] = jsonFormat3(Pod.apply)
  implicit val podListFormat: RootJsonFormat[PodList] = jsonFormat1(PodList.apply)
}
