/*
 * Copyright (C) 2017 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.discovery.kubernetes

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json._

import PodList._

object JsonFormat extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val containerPortFormat: JsonFormat[ContainerPort] = jsonFormat2(ContainerPort)
  implicit val containerFormat: JsonFormat[Container] = jsonFormat2(Container)
  implicit val podSpecFormat: JsonFormat[PodSpec] = jsonFormat1(PodSpec)
  implicit val podStatusFormat: JsonFormat[PodStatus] = jsonFormat1(PodStatus)
  implicit val metadataFormat: JsonFormat[Metadata] = jsonFormat1(Metadata)
  implicit val podFormat: JsonFormat[Pod] = jsonFormat3(Pod)
  implicit val podListFormat: RootJsonFormat[PodList] = jsonFormat1(PodList.apply)
}
