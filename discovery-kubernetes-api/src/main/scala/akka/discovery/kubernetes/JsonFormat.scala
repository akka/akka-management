/*
 * Copyright (C) 2017 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.discovery.kubernetes

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json._

import PodList._

object JsonFormat extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val portFormat: JsonFormat[Port] = jsonFormat2(Port)
  implicit val containerFormat: JsonFormat[Container] = jsonFormat2(Container)
  implicit val specFormat: JsonFormat[Spec] = jsonFormat1(Spec)
  implicit val statusFormat: JsonFormat[Status] = jsonFormat1(Status)
  implicit val metaDataFormat: JsonFormat[Metadata] = jsonFormat1(Metadata)
  implicit val itemFormat: JsonFormat[Item] = jsonFormat3(Item)
  implicit val podListFormat: RootJsonFormat[PodList] = jsonFormat1(PodList.apply)
}
