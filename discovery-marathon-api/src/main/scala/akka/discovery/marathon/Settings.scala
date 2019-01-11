/*
 * Copyright (C) 2017-2018 Lightbend Inc. <http://www.lightbend.com>
 */

package akka.discovery.marathon

import akka.actor._

final class Settings(system: ExtendedActorSystem) extends Extension {
  private val marathonApi = system.settings.config.getConfig("akka.discovery.marathon-api")

  val appApiUrl: String =
    marathonApi.getString("app-api-url")

  val appPortName: String =
    marathonApi.getString("app-port-name")

  val appLabelQuery: String =
    marathonApi.getString("app-label-query")

  private val authentication = marathonApi.getConfig("authentication")

  val authType: String =
    authentication.getString("type")

  val authLoginUrl: String =
    authentication.getString("login-url")

  val authUsername: String =
    authentication.getString("username")

  val authPassword: String =
    authentication.getString("password")

  val authServiceAccountId: String =
    authentication.getString("service-account-id")

  val authPrivateKey: String =
    authentication.getString("private-key")

  val authAlgorithm: String =
    authentication.getString("algorithm")

  val authExpiry: Int =
    authentication.getInt("expiry")
}

object Settings extends ExtensionId[Settings] with ExtensionIdProvider {
  override def get(system: ActorSystem): Settings = super.get(system)

  override def lookup: Settings.type = Settings

  override def createExtension(system: ExtendedActorSystem): Settings = new Settings(system)
}
