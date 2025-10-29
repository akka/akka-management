/*
 * Copyright (C) 2017-2025 Lightbend Inc. <https://akka.io>
 */

package akka.discovery.kubernetes

import java.util.Optional
import akka.actor._
import com.typesafe.config.Config

import scala.concurrent.duration.FiniteDuration
import scala.jdk.DurationConverters._
import scala.jdk.OptionConverters._

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

  private val kubernetesApi = system.settings.config.getConfig("akka.discovery.kubernetes-api")

  val apiCaPath: String =
    kubernetesApi.getString("api-ca-path")

  val apiTokenPath: String =
    kubernetesApi.getString("api-token-path")

  val apiServiceHostEnvName: String =
    kubernetesApi.getString("api-service-host-env-name")

  val apiServicePortEnvName: String =
    kubernetesApi.getString("api-service-port-env-name")

  val apiTokenTtl: FiniteDuration =
    kubernetesApi.getDuration("api-token-reload-interval").toScala

  val podNamespacePath: String =
    kubernetesApi.getString("pod-namespace-path")

  /** Scala API */
  val podNamespace: Option[String] =
    kubernetesApi.optDefinedValue("pod-namespace")

  /** Java API */
  def getPodNamespace: Optional[String] = podNamespace.toJava

  val podDomain: String =
    kubernetesApi.getString("pod-domain")

  def podLabelSelector(name: String): String =
    kubernetesApi.getString("pod-label-selector").format(name)

  lazy val rawIp: Boolean = kubernetesApi.getBoolean("use-raw-ip")

  val containerName: Option[String] = Some(kubernetesApi.getString("container-name")).filter(_.nonEmpty)

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
