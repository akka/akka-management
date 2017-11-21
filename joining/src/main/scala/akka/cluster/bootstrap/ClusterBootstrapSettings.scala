/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.cluster.bootstrap

import java.util.Locale

import akka.actor.ActorSystem
import akka.stream.scaladsl.{ Sink, Source }
import com.typesafe.config.Config

import scala.concurrent.duration._
import scala.concurrent.duration.FiniteDuration

// FIXME make easy to binary evolve (kaze class)
// FIXME don't hardcode
// FIXME FIXME !! redesign the settings !! half of those are kubernetes and DNS specific
/**
 *
 * @param serviceName if None the actor system name will be used
 */
final case class ClusterBootstrapSettings(
    bootstrapMethod: String = "dns-svc",
    serviceName: Option[String] = Some("appka-service"), // k8s specific ??
    namespaceName: Option[String] = Some("default"), // k8s specific ??
    dnsSuffix: String = ".svc.cluster.local", // k8s specific ??
    requiredContactPoints: Int = 3, // TODO this is a hard lower limit, we should have that to avoid "single node" joining itself?
    expectedContactPoints: Int = 4, // if we see that many contact points, we could ignore the stable timeout and do as fast-join?
    httpProbeInterval: FiniteDuration = 5.seconds,
    httpProbeIntervalJitter: Double = 0.2, // +/- 20%
    httpMaxSeedNodesToExpose: Int = 5,
    contactPointPort: Int = 8558, // TODO I wonder if we could discover those as well, but likely not needed to be honest?
    stableMargin: FiniteDuration = 10.seconds,
    dnsResolveTimeout: FiniteDuration = 30.seconds
) {

  require(requiredContactPoints > 2, "Number of contact points is strongly recommended to be greater than")

  /**
   * Effective name is combination of service name and other namespaces,
   * e.g. `appka-service.my-namespace.svc.cluster.local`.
   */
  def effectiveServiceName(system: ActorSystem) = {
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
