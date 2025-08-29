/*
 * Copyright (C) 2017-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.rollingupdate.kubernetes

import akka.annotation.InternalApi
import com.typesafe.config.Config

import scala.concurrent.duration._
import scala.jdk.DurationConverters._

/**
 * INTERNAL API
 */
@InternalApi
private[kubernetes] object KubernetesSettings {

  private implicit class HasDefined(val config: Config) {
    def hasDefined(key: String): Boolean =
      config.hasPath(key) &&
      config.getString(key).trim.nonEmpty &&
      config.getString(key) != s"<$key>"

    def optDefinedValue(key: String): Option[String] =
      if (hasDefined(key)) Some(config.getString(key)) else None
  }

  def apply(config: Config): KubernetesSettings = {
    val crName = config.getString("custom-resource.cr-name") match {
      case ""   => None
      case name => Some(name)
    }

    val customResourceSettings = new CustomResourceSettings(
      enabled = config.getBoolean("custom-resource.enabled"),
      crName = crName,
      cleanupAfter = config.getDuration("custom-resource.cleanup-after").toScala
    )

    new KubernetesSettings(
      config.getString("api-ca-path"),
      config.getString("api-token-path"),
      config.getString("api-service-host"),
      config.getInt("api-service-port"),
      config.getDuration("api-token-reload-interval").toScala,
      config.optDefinedValue("namespace"),
      config.getString("namespace-path"),
      config.getString("pod-name"),
      config.getBoolean("secure-api-server"),
      config.getDuration("api-service-request-timeout").toScala,
      customResourceSettings,
      config.getString("revision-annotation")
    )
  }
}

/**
 * INTERNAL API
 */
@InternalApi
private[kubernetes] class KubernetesSettings(
    val apiCaPath: String,
    val apiTokenPath: String,
    val apiServiceHost: String,
    val apiServicePort: Int,
    val apiTokenTtl: FiniteDuration,
    val namespace: Option[String],
    val namespacePath: String,
    val podName: String,
    val secure: Boolean,
    val apiServiceRequestTimeout: FiniteDuration,
    val customResourceSettings: CustomResourceSettings,
    val revisionAnnotation: String,
    val bodyReadTimeout: FiniteDuration = 1.second,
    // Note: for token testability
    val insecureTokens: Boolean = false
)

/**
 * INTERNAL API
 */
@InternalApi
private[kubernetes] class CustomResourceSettings(
    val enabled: Boolean,
    val crName: Option[String],
    val cleanupAfter: FiniteDuration
)
