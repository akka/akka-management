/*
 * Copyright (C) 2017-2018 Lightbend Inc. <http://www.lightbend.com>
 */

package akka.management.cluster.bootstrap

import java.util.Locale
import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import com.typesafe.config.Config

import scala.concurrent.duration.{ FiniteDuration, _ }

final class ClusterBootstrapSettings(config: Config, log: LoggingAdapter) {
  import akka.management.AkkaManagementSettings._

  val managementBasePath: Option[String] =
    Option(config.getString("akka.management.http.base-path")).filter(_.trim.nonEmpty)

  private val bootConfig = config.getConfig("akka.management.cluster.bootstrap")

  val newClusterEnabled: Boolean = {
    if (bootConfig.hasPath("form-new-cluster")) {
      val enabled = bootConfig.getBoolean("form-new-cluster")
      log.info(
          "Old `form-new-cluster` property set. Using value {} as `new-cluster-enabled` and ignoring `new-cluster-enabled`. Please update to using `new-cluster-enabled`")
      enabled
    } else {
      bootConfig.getBoolean("new-cluster-enabled")
    }
  }

  object contactPointDiscovery {
    private val discoveryConfig: Config = bootConfig.getConfig("contact-point-discovery")

    val serviceName: Option[String] = discoveryConfig.optDefinedValue("service-name")

    val serviceNamespace: Option[String] = discoveryConfig.optDefinedValue("service-namespace")

    val portName: Option[String] = discoveryConfig.optValue("port-name")

    val protocol: Option[String] = discoveryConfig.optValue("protocol")

    def effectiveName(system: ActorSystem): String =
      discoveryConfig.optDefinedValue("effective-name").getOrElse {
        val service =
          serviceName.getOrElse(system.name.toLowerCase(Locale.ROOT).replaceAll(" ", "-").replace("_", "-"))

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
  def apply(config: Config, log: LoggingAdapter): ClusterBootstrapSettings =
    new ClusterBootstrapSettings(config, log)
}
