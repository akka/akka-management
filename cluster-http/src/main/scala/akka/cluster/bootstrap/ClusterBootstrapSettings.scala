/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.cluster.bootstrap

import java.util.Locale
import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import com.typesafe.config.Config

import scala.concurrent.duration.{ FiniteDuration, _ }

// FIXME make easy to binary evolve (kaze class)
// FIXME don't hard-code
// TODO security for the contact point requests?
// FIXME FIXME !! redesign the settings !! half of those are kubernetes and DNS specific
final class ClusterBootstrapSettings(config: Config) {
  private val bootConfig = config.getConfig("akka.cluster.bootstrap")

  object contactPointDiscovery {
    private val discoveryConfig: Config = bootConfig.getConfig("contact-point-discovery")

    val serviceName: Option[String] =
      if (discoveryConfig.hasPath("service-name")) Some(discoveryConfig.getString("service-name")) else None

    val serviceNamespace: Option[String] =
      if (discoveryConfig.hasPath("service-namespace")) Some(discoveryConfig.getString("service-namespace")) else None

    def effectiveName(system: ActorSystem): String = {
      val service = serviceName match {
        case Some(name) ⇒ name
        case _ ⇒ system.name.toLowerCase(Locale.ROOT).replaceAll(" ", "-").replace("_", "-")
      }
      val namespace = serviceNamespace match {
        case Some(ns) ⇒ s".$ns"
        case _ ⇒ ""
      }
      if (discoveryConfig.hasPath("effective-name")) discoveryConfig.getString("effective-name")
      else service + namespace
    }

    val discoveryMethod: String = discoveryConfig.getString("discovery-method")

    private val effectiveDiscoveryConfig: Config = discoveryConfig.withFallback(config.getConfig(discoveryMethod))
    val discoveryClass: String = effectiveDiscoveryConfig.getString("class")

    val stableMargin: FiniteDuration =
      effectiveDiscoveryConfig.getDuration("stable-margin", TimeUnit.MILLISECONDS).millis

    val interval: FiniteDuration =
      effectiveDiscoveryConfig.getDuration("interval", TimeUnit.MILLISECONDS).millis

    val requiredContactPointsNr = discoveryConfig.getInt("required-contact-point-nr")
    require(requiredContactPointsNr >= 2, "Number of contact points ")

    val resolveTimeout = discoveryConfig.getDuration("resolve-timeout", TimeUnit.MILLISECONDS).millis

  }

  object contactPoint {
    private val contactPointConfig = bootConfig.getConfig("contact-point")

    // FIXME this has to be the same as the management one, we currently override this value when starting management, any better way?
    val fallbackPort = contactPointConfig.getInt("fallback-port")

    val noSeedsStableMargin: FiniteDuration =
      contactPointConfig.getDuration("no-seeds-stable-margin", TimeUnit.MILLISECONDS).millis

    val probeInterval: FiniteDuration =
      contactPointConfig.getDuration("probe-interval", TimeUnit.MILLISECONDS).millis

    val probeIntervalJitter: Double =
      contactPointConfig.getDouble("probe-interval-jitter")

    val probeTimeout: FiniteDuration =
      contactPointConfig.getDuration("probe-timeout", TimeUnit.MILLISECONDS).millis

    val httpMaxSeedNodesToExpose: Int = 5
  }

}

object ClusterBootstrapSettings {
  def apply(config: Config): ClusterBootstrapSettings =
    new ClusterBootstrapSettings(config)
}
