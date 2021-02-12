/*
 * Copyright (C) 2017-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.coordination.lease.kubernetes

import akka.actor.ActorSystem
import akka.annotation.InternalApi
import akka.coordination.lease.TimeoutSettings
import akka.util.JavaDurationConverters._
import com.typesafe.config.Config

import scala.concurrent.duration.{ FiniteDuration, _ }

/**
 * INTERNAL API
 */
@InternalApi
private[akka] object KubernetesSettings {

  private implicit class HasDefined(val config: Config) {
    def hasDefined(key: String): Boolean =
      config.hasPath(key) &&
      config.getString(key).trim.nonEmpty &&
      config.getString(key) != s"<$key>"

    def optDefinedValue(key: String): Option[String] =
      if (hasDefined(key)) Some(config.getString(key)) else None
  }

  def apply(system: ActorSystem, leaseTimeoutSettings: TimeoutSettings): KubernetesSettings = {
    apply(system.settings.config.getConfig(KubernetesLease.configPath), leaseTimeoutSettings)
  }
  def apply(config: Config, leaseTimeoutSettings: TimeoutSettings): KubernetesSettings = {

    val apiServerRequestTimeout =
      if (config.hasDefined("api-server-request-timeout"))
        config.getDuration("api-server-request-timeout").asScala
      else
        leaseTimeoutSettings.operationTimeout * 2 / 5 // 2/5 gives two API operations + a buffer

    require(
      apiServerRequestTimeout < leaseTimeoutSettings.operationTimeout,
      "'api-server-request-timeout can not be less than 'lease-operation-timeout'")

    new KubernetesSettings(
      config.getString("api-ca-path"),
      config.getString("api-token-path"),
      config.getString("api-service-host"),
      config.getInt("api-service-port"),
      config.optDefinedValue("namespace"),
      config.getString("namespace-path"),
      apiServerRequestTimeout,
      secure = config.getBoolean("secure-api-server"),
      apiServerRequestTimeout / 2)

  }
}

/**
 * INTERNAL API
 */
@InternalApi
private[akka] class KubernetesSettings(
    val apiCaPath: String,
    val apiTokenPath: String,
    val apiServerHost: String,
    val apiServerPort: Int,
    val namespace: Option[String],
    val namespacePath: String,
    val apiServerRequestTimeout: FiniteDuration,
    val secure: Boolean = true,
    val bodyReadTimeout: FiniteDuration = 1.second)
