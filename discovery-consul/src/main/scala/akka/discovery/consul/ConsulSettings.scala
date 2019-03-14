/*
 * Copyright (C) 2017-2018 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.discovery.consul

import akka.actor.{ ActorSystem, ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider }
import akka.annotation.ApiMayChange

@ApiMayChange
final class ConsulSettings(system: ExtendedActorSystem) extends Extension {
  private val consulConfig = system.settings.config.getConfig("akka.discovery.akka-consul")

  val consulHost: String = consulConfig.getString("consul-host")

  val consulPort: Int = consulConfig.getInt("consul-port")

  val applicationNameTagPrefix: String = consulConfig.getString("application-name-tag-prefix")
  val applicationAkkaManagementPortTagPrefix: String =
    consulConfig.getString("application-akka-management-port-tag-prefix")
}

@ApiMayChange
object ConsulSettings extends ExtensionId[ConsulSettings] with ExtensionIdProvider {
  override def get(system: ActorSystem): ConsulSettings = super.get(system)

  override def lookup: ConsulSettings.type = ConsulSettings

  override def createExtension(system: ExtendedActorSystem): ConsulSettings = new ConsulSettings(system)
}
