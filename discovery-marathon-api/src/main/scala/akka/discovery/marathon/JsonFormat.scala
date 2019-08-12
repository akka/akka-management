/*
 * Copyright (C) 2017-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.discovery.marathon

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json._
import AppList._

object JsonFormat extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val taskFormat: JsonFormat[Task] = jsonFormat2(Task)
  implicit val portDefinitionFormat: JsonFormat[PortDefinition] = jsonFormat2(PortDefinition)
  implicit val portMappingFormat: JsonFormat[PortMapping] = jsonFormat2(PortMapping)
  implicit val dockerFormat: JsonFormat[Docker] = jsonFormat1(Docker)
  implicit val containerFormat: JsonFormat[Container] = jsonFormat2(Container)
  implicit val appFormat: JsonFormat[App] = jsonFormat3(App)
  implicit val appListFormat: RootJsonFormat[AppList] = jsonFormat1(AppList.apply)
}
