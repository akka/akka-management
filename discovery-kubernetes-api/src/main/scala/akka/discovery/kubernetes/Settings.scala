/*
 * Copyright (C) 2017 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.discovery.kubernetes

import akka.actor._

final class Settings(system: ExtendedActorSystem) extends Extension {
  private val kubernetesApi = system.settings.config.getConfig("akka.discovery.kubernetes-api")

  val apiCaPath: String =
    kubernetesApi.getString("api-ca-path")

  val apiTokenPath: String =
    kubernetesApi.getString("api-token-path")

  val apiServiceHostEnvName: String =
    kubernetesApi.getString("api-service-host-env-name")

  val apiServicePortEnvName: String =
    kubernetesApi.getString("api-service-port-env-name")

  val podNamespace: String =
    kubernetesApi.getString("pod-namespace")

  def podLabelSelector(name: String): String =
    kubernetesApi.getString("pod-label-selector").format(name)

  val podPortName: String =
    kubernetesApi.getString("pod-port-name")
}

object Settings extends ExtensionId[Settings] with ExtensionIdProvider {
  override def get(system: ActorSystem): Settings = super.get(system)

  override def lookup: Settings.type = Settings

  override def createExtension(system: ExtendedActorSystem): Settings = new Settings(system)
}
