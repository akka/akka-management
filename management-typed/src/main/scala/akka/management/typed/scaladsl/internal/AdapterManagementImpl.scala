package akka.management.typed.scaladsl.internal

import akka.Done
import akka.annotation.InternalApi
import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.server.Route
import akka.management.AkkaManagementSettings
import akka.management.scaladsl.ManagementRouteProviderSettings
import akka.management.typed.scaladsl.AkkaManagement
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.adapter._

import scala.concurrent.Future

/**
 * INTERNAL API:
 */
@InternalApi
private[akka] final class AdapterManagementImpl(system: ActorSystem[_]) extends AkkaManagement {
  private val untypedManagement = akka.management.scaladsl.AkkaManagement(system.toUntyped)

  override val settings: AkkaManagementSettings = untypedManagement.settings

  override def routes: Route = untypedManagement.routes
  override def routes(transformSettings: ManagementRouteProviderSettings => ManagementRouteProviderSettings): Route =
    untypedManagement.routes(transformSettings)

  override def start(): Future[Uri] = untypedManagement.start()
  override def start(transformSettings: ManagementRouteProviderSettings => ManagementRouteProviderSettings): Future[Uri] =
    untypedManagement.start(transformSettings)

  override def stop(): Future[Done] = untypedManagement.stop()
}
