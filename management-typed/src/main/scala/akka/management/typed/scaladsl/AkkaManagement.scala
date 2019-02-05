package akka.management.typed.scaladsl

import akka.Done
import akka.annotation.DoNotInherit
import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.server.Route
import akka.management.AkkaManagementSettings
import akka.management.scaladsl.ManagementRouteProviderSettings
import akka.management.typed.scaladsl.internal.AdapterManagementImpl
import akka.actor.typed.{ActorSystem, Extension, ExtensionId}

import scala.concurrent.Future

@DoNotInherit
abstract class AkkaManagement extends Extension {
  val settings: AkkaManagementSettings

  def routes: Route
  def routes(transformSettings: ManagementRouteProviderSettings => ManagementRouteProviderSettings): Route

  def start(): Future[Uri]
  def start(transformSettings: ManagementRouteProviderSettings => ManagementRouteProviderSettings): Future[Uri]

  def stop(): Future[Done]
}

object AkkaManagement extends ExtensionId[AkkaManagement] {
  override def createExtension(system: ActorSystem[_]): AkkaManagement = new AdapterManagementImpl(system)
}
