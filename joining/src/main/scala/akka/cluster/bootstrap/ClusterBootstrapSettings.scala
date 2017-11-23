/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.cluster.bootstrap

import java.util.Locale

import akka.actor.ActorSystem
import com.typesafe.config.Config

import scala.concurrent.duration._
import scala.concurrent.duration.FiniteDuration

// FIXME make easy to binary evolve (kaze class)
// FIXME don't hard-code
// TODO security for the contact point requests?
// FIXME FIXME !! redesign the settings !! half of those are kubernetes and DNS specific
/**
 *
 * @param serviceName if None the actor system name will be used
 */
final case class ClusterBootstrapSettings(
    bootstrapMethod: String = "dns-svc",
    serviceName: Option[String] = Some("appka-service"), // k8s specific ??
    namespaceName: Option[String] = Some("default"), // k8s specific ??

    // TODO make those DNS lookup settings
    dnsSuffix: String = ".svc.cluster.local", // k8s specific ??
    dnsStableMargin: FiniteDuration = 3.seconds,
    dnsResolveInterval: FiniteDuration = 1.second, // should be rather aggressive
    dnsResolveTimeout: FiniteDuration = 30.seconds,
    requiredContactPointsNr: Int = 3, // TODO this is a hard lower limit, we should have that to avoid "single node" joining itself?

    // TODO make those http contact point settings
    httpContactPointPort: Int = 8558, // TODO I wonder if we could discover those as well, but likely not needed to be honest?
    contactPointNoSeedsStableMargin: FiniteDuration = 10.seconds,
    httpProbeInterval: FiniteDuration = 2.second,
    httpProbeIntervalJitter: Double = 0.2, // +/- 20%
    httpMaxSeedNodesToExpose: Int = 5
) {

  require(requiredContactPointsNr > 2, "Number of contact points is strongly recommended to be greater than 2")
  require(contactPointNoSeedsStableMargin - dnsStableMargin > 1.second,
    s"The dnsStableMargin ($dnsStableMargin) MUST be " +
    s"at least [1 second] shorter than the contactPointNoSeedsStableMargin ($contactPointNoSeedsStableMargin)")

  /**
   * Effective name is combination of service name and other namespaces,
   * e.g. `appka-service.my-namespace.svc.cluster.local`.
   */
  def effectiveServiceName(system: ActorSystem): String = {
    val service = serviceName match {
      case Some(name) ⇒ name
      case _ ⇒ system.name.toLowerCase(Locale.ROOT).replaceAll(" ", "-").replace("_", "-")
    }
    val ns = namespaceName match {
      case Some(name) ⇒ s".$name"
      case _ ⇒ ""
    }

    s"$service$ns$dnsSuffix"
  }
}

object ClusterBootstrapSettings {
  def apply(c: Config): ClusterBootstrapSettings =
    // FIXME actual impl
    new ClusterBootstrapSettings()

}
