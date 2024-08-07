/*
 * Copyright (C) 2017-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.discovery.azureapi

import akka.actor.{
  ActorSystem,
  ClassicActorSystemProvider,
  ExtendedActorSystem,
  Extension,
  ExtensionId,
  ExtensionIdProvider
}
import com.typesafe.config.Config

import java.util.Optional
import scala.compat.java8.OptionConverters._

final class Settings(system: ExtendedActorSystem) extends Extension {

  /**
   * Copied from AkkaManagementSettings, which we don't depend on.
   */
  private implicit class HasDefined(val config: Config) {
    def hasDefined(key: String): Boolean =
      config.hasPath(key) &&
      config.getString(key).trim.nonEmpty &&
      config.getString(key) != s"<$key>"

    def optDefinedValue(key: String): Option[String] =
      if (hasDefined(key)) Some(config.getString(key)) else None
  }

  private val config = system.settings.config.getConfig("akka.discovery.azure-rbac-aks-api")

  val authorityHost: String =
    config.getString("authority-host")

  val clientId: String =
    config.getString("client-id")

  val federatedTokenPath: String =
    config.getString("federated-token-file")

  val tenantId: String =
    config.getString("tenant-id")

  val entraServerId: String =
    config.getString("entra-server-id")

  val apiCaPath: String =
    config.getString("api-ca-path")

  val apiTokenPath: String =
    config.getString("api-token-path")

  val apiServiceHostEnvName: String =
    config.getString("api-service-host-env-name")

  val apiServicePortEnvName: String =
    config.getString("api-service-port-env-name")

  val podNamespacePath: String =
    config.getString("pod-namespace-path")

  /** Scala API */
  val podNamespace: Option[String] =
    config.optDefinedValue("pod-namespace")

  /** Java API */
  def getPodNamespace: Optional[String] = podNamespace.asJava

  val podDomain: String =
    config.getString("pod-domain")

  def podLabelSelector(name: String): String =
    config.getString("pod-label-selector").format(name)

  lazy val rawIp: Boolean = config.getBoolean("use-raw-ip")

  val containerName: Option[String] = Some(config.getString("container-name")).filter(_.nonEmpty)

  override def toString =
    s"Settings($apiCaPath, $apiTokenPath, $apiServiceHostEnvName, $apiServicePortEnvName, " +
    s"$podNamespacePath, $podNamespace, $podDomain)"
}

object Settings extends ExtensionId[Settings] with ExtensionIdProvider {
  override def get(system: ActorSystem): Settings = super.get(system)

  override def get(system: ClassicActorSystemProvider): Settings = super.get(system)

  override def lookup: Settings.type = Settings

  override def createExtension(system: ExtendedActorSystem): Settings = new Settings(system)
}
