/*
 * Copyright (C) 2017-2025 Lightbend Inc. <https://akka.io>
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
  // If adding more formats here, remember to also add in META-INF/native-image reflect config
  implicit val containerPortFormat: JsonFormat[ContainerPort] = jsonFormat2(ContainerPort.apply)
  implicit val containerFormat: JsonFormat[Container] = jsonFormat2(Container.apply)
  implicit val podSpecFormat: JsonFormat[PodSpec] = jsonFormat1(PodSpec.apply)
  implicit val containerStatusFormat: JsonFormat[ContainerStatus] = jsonFormat2(ContainerStatus.apply)
  implicit val podConditionFormat: JsonFormat[PodCondition] = jsonFormat2(PodCondition.apply)
  implicit val podStatusFormat: JsonFormat[PodStatus] = jsonFormat4(PodStatus.apply)
  implicit val metadataFormat: JsonFormat[Metadata] = jsonFormat1(Metadata.apply)
  implicit val podFormat: JsonFormat[Pod] = jsonFormat3(Pod.apply)
  implicit val podListFormat: RootJsonFormat[PodList] = jsonFormat1(PodList.apply)
}
