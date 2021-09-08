/*
 * Copyright (C) 2017-2021 Lightbend Inc. <https://www.lightbend.com>
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
  implicit val containerPortFormat: JsonFormat[ContainerPort] = jsonFormat2(ContainerPort)
  implicit val containerFormat: JsonFormat[Container] = jsonFormat2(Container)
  implicit val podSpecFormat: JsonFormat[PodSpec] = jsonFormat1(PodSpec)
  implicit val containerStatusFormat: JsonFormat[ContainerStatus] = jsonFormat2(ContainerStatus)
  implicit val podStatusFormat: JsonFormat[PodStatus] = jsonFormat3(PodStatus)
  implicit val metadataFormat: JsonFormat[Metadata] = jsonFormat1(Metadata)
  implicit val podFormat: JsonFormat[Pod] = jsonFormat3(Pod)
  implicit val podListFormat: RootJsonFormat[PodList] = jsonFormat1(PodList.apply)
}
