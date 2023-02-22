/*
 * Copyright (C) 2017-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.rollingupdate.kubernetes

import akka.actor._
import com.typesafe.config.Config

import java.util.Optional
import scala.compat.java8.OptionConverters._

final class KubernetesSettings(system: ExtendedActorSystem) extends Extension {

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

  private val kubernetesApi = system.settings.config.getConfig("akka.rollingupdate.kubernetes")

  val apiCaPath: String =
    kubernetesApi.getString("api-ca-path")

  val apiTokenPath: String =
    kubernetesApi.getString("api-token-path")

  val apiServiceHost: String =
    kubernetesApi.getString("api-service-host")

  val apiServicePort: String =
    kubernetesApi.getString("api-service-port")

  val podNamespacePath: String =
    kubernetesApi.getString("namespace-path")

  /** Scala API */
  val podNamespace: Option[String] =
    kubernetesApi.optDefinedValue("namespace")

  /** Java API */
  def getPodNamespace: Optional[String] = podNamespace.asJava

  val podName: String =
    kubernetesApi.getString("pod-name")

  val secure: Boolean = kubernetesApi.getBoolean("secure-api-server")

  override def toString =
    s"PodDeletionCostSettings($apiCaPath, $apiTokenPath, $apiServiceHost, $apiServicePort, " +
    s"$podNamespacePath, $podNamespace, $podName, $secure)"
}

object KubernetesSettings extends ExtensionId[KubernetesSettings] with ExtensionIdProvider {
  override def get(system: ActorSystem): KubernetesSettings = super.get(system)

  override def get(system: ClassicActorSystemProvider): KubernetesSettings = super.get(system)

  override def lookup: KubernetesSettings.type = KubernetesSettings

  override def createExtension(system: ExtendedActorSystem): KubernetesSettings = new KubernetesSettings(system)
}
