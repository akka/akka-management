/*
 * Copyright (C) 2017 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.management.cluster.bootstrap

import java.util.Locale
import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import com.typesafe.config.Config

import scala.concurrent.duration.{ FiniteDuration, _ }

final class ClusterBootstrapSettings(config: Config) {
  private implicit class HasDefined(val config: Config) {
    def hasDefined(key: String): Boolean =
      config.hasPath(key) &&
      config.getString(key).trim.nonEmpty &&
      config.getString(key) != s"<$key>"
  }

  val managementBasePath: Option[String] =
    Option(config.getString("akka.management.http.base-path")).filter(_.trim.nonEmpty)

  private val bootConfig = config.getConfig("akka.management.cluster.bootstrap")

  val formNewCluster: Boolean = bootConfig.getBoolean("form-new-cluster")

  object contactPointDiscovery {
    private val discoveryConfig: Config = bootConfig.getConfig("contact-point-discovery")

    val serviceName: Option[String] =
      if (discoveryConfig.hasDefined("service-name")) Some(discoveryConfig.getString("service-name"))
      else None

    val serviceNamespace: Option[String] =
      if (discoveryConfig.hasDefined("service-namespace")) Some(discoveryConfig.getString("service-namespace"))
      else None

    val portName = getOptionalString("port-name")

    val protocol = getOptionalString("protocol")

    def effectiveName(system: ActorSystem): String =
      if (discoveryConfig.hasDefined("effective-name")) {
        discoveryConfig.getString("effective-name")
      } else {
        val service = serviceName match {
          case Some(name) ⇒ name
          case _ ⇒ system.name.toLowerCase(Locale.ROOT).replaceAll(" ", "-").replace("_", "-")
        }
        val namespace = serviceNamespace match {
          case Some(ns) ⇒ s".$ns"
          case _ ⇒ ""
        }
        service + namespace
      }

    val discoveryMethod: String = discoveryConfig.getString("discovery-method")

    val stableMargin: FiniteDuration =
      discoveryConfig.getDuration("stable-margin", TimeUnit.MILLISECONDS).millis

    val interval: FiniteDuration =
      discoveryConfig.getDuration("interval", TimeUnit.MILLISECONDS).millis

    val exponentialBackoffRandomFactor: Double =
      discoveryConfig.getDouble("exponential-backoff-random-factor")

    val exponentialBackoffMax: FiniteDuration =
      discoveryConfig.getDuration("exponential-backoff-max", TimeUnit.MILLISECONDS).millis

    require(exponentialBackoffMax >= interval, "exponential-backoff-max has to be greater or equal to interval")

    val requiredContactPointsNr: Int = discoveryConfig.getInt("required-contact-point-nr")

    val resolveTimeout: FiniteDuration = discoveryConfig.getDuration("resolve-timeout", TimeUnit.MILLISECONDS).millis

    private def getOptionalString(path: String): Option[String] = discoveryConfig.getString(path) match {
      case "" => None
      case other => Some(other)
    }

  }

  object contactPoint {
    private val contactPointConfig = bootConfig.getConfig("contact-point")

    // FIXME this has to be the same as the management one, we currently override this value when starting management, any better way?
    val fallbackPort = contactPointConfig.getInt("fallback-port")

    val probingFailureTimeout: FiniteDuration =
      contactPointConfig.getDuration("probing-failure-timeout", TimeUnit.MILLISECONDS).millis

    val probeInterval: FiniteDuration =
      contactPointConfig.getDuration("probe-interval", TimeUnit.MILLISECONDS).millis

    val probeIntervalJitter: Double =
      contactPointConfig.getDouble("probe-interval-jitter")

    val httpMaxSeedNodesToExpose: Int = 5
  }

  object joinDecider {
    val implClass: String = bootConfig.getString("join-decider.class")
  }

}

object ClusterBootstrapSettings {
  def apply(config: Config): ClusterBootstrapSettings =
    new ClusterBootstrapSettings(config)
}
