/*
 * Copyright (C) 2017-2018 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.management.cluster.bootstrap.internal
import akka.actor.Props
import akka.annotation.InternalApi
import akka.cluster.Cluster
import akka.discovery.ServiceDiscovery.ResolvedTarget
import akka.management.cluster.bootstrap.ClusterBootstrapSettings
import akka.management.cluster.bootstrap.contactpoint.HttpBootstrapJsonProtocol
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.Future

@InternalApi
private[bootstrap] object RemotingContactPointBootstrap {
  def props(settings: ClusterBootstrapSettings, contactPoint: ResolvedTarget): Props =
    Props(new RemotingContactPointBootstrap(settings, contactPoint))
}

@InternalApi
private[bootstrap] final class RemotingContactPointBootstrap(
  settings: ClusterBootstrapSettings,
  contactPoint: ResolvedTarget
) extends AbstractContactPointBootstrap(settings, contactPoint) {

  override protected val uri: String = {
    val cluster = Cluster(context.system)
    val targetPort = contactPoint.port
      .orElse(cluster.selfAddress.port)
      .getOrElse(throw new IllegalArgumentException("Cannot infer port for contact point"))
    val address = cluster.selfAddress.copy(host = Some(contactPoint.host), port = Some(targetPort))
    (self.path.parent.parent / RemotingContactPoint.RemotingContactPointActorName).toStringWithAddress(address)
  }

  private val remoteContactPoint = context.system.actorSelection(uri)

  /**
    * Probe the contact point.
    *
    * @param probingFailureTimeout A timeout, if not replied within this timeout, the returned Future should fail.
    * @return A future of the seed nodes.
    */
  override protected def probe()(implicit probingFailureTimeout: Timeout): Future[HttpBootstrapJsonProtocol.SeedNodes] = {
    (remoteContactPoint ? RemotingContactPoint.GetSeedNodes).mapTo[HttpBootstrapJsonProtocol.SeedNodes]
  }
}
