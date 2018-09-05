/*
 * Copyright (C) 2017 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.discovery.marathon.services

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json._

object JsonFormat extends SprayJsonSupport with DefaultJsonProtocol {
  case class AppList(apps: Seq[App])

  // apps
  implicit val ipAddressFormat: JsonFormat[App.IpAddress] =
    jsonFormat1(App.IpAddress)

  implicit val taskFormat: JsonFormat[App.Task] =
    jsonFormat2(App.Task)

  implicit val portDefinitionFormat: JsonFormat[App.PortDefinition] =
    jsonFormat1(App.PortDefinition)

  implicit val portMappingFormat: JsonFormat[App.PortMapping] =
    jsonFormat2(App.PortMapping)

  implicit val appContainerFormat: JsonFormat[App.Container] =
    jsonFormat1(App.Container)

  implicit val appNetworkFormat: JsonFormat[App.Network] =
    jsonFormat1(App.Network)

  implicit val appFormat: JsonFormat[App] =
    jsonFormat5(App.apply)

  implicit val appListFormat: RootJsonFormat[AppList] =
    jsonFormat1(AppList.apply)

  // pods
  implicit val endpointFormat: JsonFormat[Pod.Endpoint] =
    jsonFormat5(Pod.Endpoint)

  implicit val podContainerFormat: JsonFormat[Pod.Container] =
    jsonFormat1(Pod.Container)

  implicit val podSpecNetworkFormat: JsonFormat[Pod.SpecNetwork] =
    jsonFormat1(Pod.SpecNetwork)

  implicit val podInstanceNetworkFormat: JsonFormat[Pod.InstanceNetwork] =
    jsonFormat2(Pod.InstanceNetwork)

  implicit val instanceFormat: JsonFormat[Pod.Instance] =
    jsonFormat2(Pod.Instance)

  implicit val specFormat: JsonFormat[Pod.Spec] =
    jsonFormat3(Pod.Spec)

  implicit val podFormat: JsonFormat[Pod] =
    jsonFormat2(Pod.apply)
}
