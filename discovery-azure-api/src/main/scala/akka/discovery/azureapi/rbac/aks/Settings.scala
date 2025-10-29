/*
 * Copyright (C) 2017-2025 Lightbend Inc. <https://akka.io>
 */

package akka.discovery.azureapi.rbac.aks

import akka.annotation.InternalApi
import com.typesafe.config.Config

/**
 * INTERNAL API
 */
@InternalApi
final case class Settings(
    authorityHost: String,
    clientId: String,
    federatedTokenPath: String,
    tenantId: String,
    entraServerId: String,
    apiCaPath: String,
    apiTokenPath: String,
    apiServiceHost: String,
    apiServicePort: Int,
    podNamespacePath: String,
    podNamespace: Option[String],
    podDomain: String,
    podLabelSelector: String,
    rawIp: Boolean,
    containerName: Option[String]
)

/**
 * INTERNAL API
 */
@InternalApi
object Settings {
  private implicit class HasDefined(val config: Config) {
    def hasDefined(key: String): Boolean =
      config.hasPath(key) &&
      config.getString(key).trim.nonEmpty &&
      config.getString(key) != s"<$key>"

    def optDefinedValue(key: String): Option[String] =
      if (hasDefined(key)) Some(config.getString(key)) else None
  }

  def apply(config: Config) = new Settings(
    authorityHost = config.getString("authority-host"),
    clientId = config.getString("client-id"),
    federatedTokenPath = config.getString("federated-token-file"),
    tenantId = config.getString("tenant-id"),
    entraServerId = config.getString("entra-server-id"),
    apiCaPath = config.getString("api-ca-path"),
    apiTokenPath = config.getString("api-token-path"),
    apiServiceHost = config.getString("api-service-host"),
    apiServicePort = config.getInt("api-service-port"),
    podNamespacePath = config.getString("pod-namespace-path"),
    podNamespace = config.optDefinedValue("pod-namespace"),
    podDomain = config.getString("pod-domain"),
    podLabelSelector = config.getString("pod-label-selector"),
    rawIp = config.getBoolean("use-raw-ip"),
    containerName = Some(config.getString("container-name")).filter(_.nonEmpty)
  )
}
