/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.cluster.bootstrap

import java.util.concurrent.TimeUnit

import com.typesafe.config.Config

import scala.concurrent.duration.{FiniteDuration, _}

// FIXME make easy to binary evolve (kaze class)
// FIXME don't hard-code
// TODO security for the contact point requests?
// FIXME FIXME !! redesign the settings !! half of those are kubernetes and DNS specific
final class ClusterBootstrapSettings(config: Config) {
  private val bootConfig = config.getConfig("akka.cluster.bootstrap")

  object contactPointDiscovery {
    private val discoveryConfig: Config = bootConfig.getConfig("contact-point-discovery")

    val serviceName: Option[String] =
      if (bootConfig.hasPath("service-name")) Some(bootConfig.getString("service-name")) else None

    val serviceNamespace: Option[String] =
      if (bootConfig.hasPath("service-namespace")) Some(bootConfig.getString("service-namespace")) else None

    val effectiveNameOverride: Option[String] =
      if (bootConfig.hasPath("effective-name-override")) Some(bootConfig.getString("effective-name-override")) else None

    val discoveryMethod: String = discoveryConfig.getString("discovery-method")

    private val effectiveDiscoveryConfig: Config = discoveryConfig.withFallback(config.getConfig(discoveryMethod))
    val discoveryClass: String = discoveryConfig.getString("discovery-method")

    val stableMargin: FiniteDuration = effectiveDiscoveryConfig.getDuration("stable-margin", TimeUnit.MILLISECONDS).millis

    val requiredContactPointsNr = discoveryConfig.getInt("required-contact-point-nr")
    require(requiredContactPointsNr >= 2, "Number of contact points ")
  }

  object contactPoint {
    val contactPointConfig = bootConfig.getConfig("contact-point")

    val portFallback = bootConfig.getInt("port-fallback")

    val httpMaxSeedNodesToExpose: Int = 5
  }

//  require(contactPointNoSeedsStableMargin - dnsStableMargin > 1.second,
//    s"The dnsStableMargin ($dnsStableMargin) MUST be " +
//      s"at least [1 second] shorter than the contactPointNoSeedsStableMargin ($contactPointNoSeedsStableMargin)")

}

final case class ClusterBootstrapSettings_NEIN(
    namespaceName: Option[String] = Some("default"), // k8s specific ??

    // TODO make those DNS lookup settings
    dnsSuffix: String = ".svc.cluster.local", // k8s specific ??
    dnsStableMargin: FiniteDuration = 3.seconds,
    dnsResolveInterval: FiniteDuration = 1.second, // should be rather aggressive
    dnsResolveTimeout: FiniteDuration = 30.seconds,
    requiredContactPointsNr: Int = 3, // TODO this is a hard lower limit, we should have that to avoid "single node" joining itself?

    // TODO make those http contact point settings
    httpContactPointFallbackPort: Int = 8558, // TODO I wonder if we could discover those as well, but likely not needed to be honest?
    contactPointNoSeedsStableMargin: FiniteDuration = 10.seconds,
    httpProbeInterval: FiniteDuration = 2.second,
    httpProbeIntervalJitter: Double = 0.2, // +/- 20%
) {


}

object ClusterBootstrapSettings {
  def apply(config: Config): ClusterBootstrapSettings =
    new ClusterBootstrapSettings(config)

}
