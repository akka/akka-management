/*
 * Copyright (C) 2017-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.management.cluster.bootstrap

import java.util.Locale
import java.util.Optional
import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import akka.event.LoggingAdapter

import com.typesafe.config.Config

import scala.concurrent.duration.{ FiniteDuration, _ }
import scala.jdk.DurationConverters._
import scala.jdk.OptionConverters._

final class ClusterBootstrapSettings(config: Config, log: LoggingAdapter) {
  import akka.management.AkkaManagementSettings._

  val managementBasePath: Option[String] =
    Option(config.getString("akka.management.http.base-path")).filter(_.trim.nonEmpty)

  def getManagementBasePath: Optional[String] = managementBasePath.toJava

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
          case Some(ns) => s".$ns"
          case _        => ""
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

    val contactWithAllContactPoints: Boolean = discoveryConfig.getBoolean("contact-with-all-contact-points")

    val resolveTimeout: FiniteDuration = discoveryConfig.getDuration("resolve-timeout", TimeUnit.MILLISECONDS).millis

  }

  /** Java API */
  def getContactPointDiscoveryServiceName: Optional[String] = contactPointDiscovery.serviceName.toJava

  /** Java API */
  def getContactPointDiscoveryServiceNamespace: Optional[String] = contactPointDiscovery.serviceNamespace.toJava

  /** Java API */
  def getContactPointDiscoveryPortName: Optional[String] = contactPointDiscovery.portName.toJava

  /** Java API */
  def getContactPointDiscoveryProtocol: Optional[String] = contactPointDiscovery.protocol.toJava

  /** Java API */
  def getContactPointDiscoveryEffectiveName(system: ActorSystem): String = contactPointDiscovery.effectiveName(system)

  /** Java API */
  def getContactPointDiscoveryMethod: String = contactPointDiscovery.discoveryMethod

  /** Java API */
  def getContactPointDiscoveryStableMargin: java.time.Duration = contactPointDiscovery.stableMargin.toJava

  /** Java API */
  def getContactPointDiscoveryInterval: java.time.Duration = contactPointDiscovery.interval.toJava

  /** Java API */
  def getContactPointDiscoveryExponentialBackoffRandomFactor: Double =
    contactPointDiscovery.exponentialBackoffRandomFactor

  /** Java API */
  def getContactPointDiscoveryExponentialBackoffMax: java.time.Duration =
    contactPointDiscovery.exponentialBackoffMax.toJava

  /** Java API */
  def getContactPointDiscoveryRequiredContactPointsNr: Int = contactPointDiscovery.requiredContactPointsNr

  /** Java API */
  def getContactPointDiscoveryResolveTimeout: java.time.Duration = contactPointDiscovery.resolveTimeout.toJava

  object contactPoint {
    private val contactPointConfig = bootConfig.getConfig("contact-point")

    val fallbackPort: Int =
      contactPointConfig
        .optDefinedValue("fallback-port")
        .map(_.toInt)
        .getOrElse(config.getInt("akka.management.http.port"))

    val filterOnFallbackPort: Boolean =
      contactPointConfig.getBoolean("filter-on-fallback-port")

    val probingFailureTimeout: FiniteDuration =
      contactPointConfig.getDuration("probing-failure-timeout", TimeUnit.MILLISECONDS).millis

    val probeInterval: FiniteDuration =
      contactPointConfig.getDuration("probe-interval", TimeUnit.MILLISECONDS).millis

    val probeIntervalJitter: Double =
      contactPointConfig.getDouble("probe-interval-jitter")

    val httpMaxSeedNodesToExpose: Int = 5
  }

  /** Java API */
  def getContactPointFallbackPort: Int = contactPoint.fallbackPort

  /** Java API */
  def getContactPointProbingFailureTimeout: java.time.Duration = contactPoint.probingFailureTimeout.toJava

  object joinDecider {
    val implClass: String = bootConfig.getString("join-decider.class")
  }

  /** Java API */
  def getJoinDeciderImplClass: String = joinDecider.implClass
}

object ClusterBootstrapSettings {
  def apply(config: Config, log: LoggingAdapter): ClusterBootstrapSettings =
    new ClusterBootstrapSettings(config, log)
}
