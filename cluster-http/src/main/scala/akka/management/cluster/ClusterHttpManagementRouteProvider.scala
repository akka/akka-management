/*
 * Copyright (C) 2017-2018 Lightbend Inc. <http://www.lightbend.com>
 */

package akka.management.cluster

import akka.actor.{ ActorSystem, ExtendedActorSystem, ExtensionId, ExtensionIdProvider }
import akka.cluster.Cluster
import akka.http.scaladsl.server.Route
import akka.management.cluster.scaladsl.ClusterHttpManagementRoutes
import akka.management.scaladsl.ManagementRouteProviderSettings
import akka.management.scaladsl.ManagementRouteProvider

object ClusterHttpManagementRouteProvider
    extends ExtensionId[ClusterHttpManagementRouteProvider]
    with ExtensionIdProvider {
  override def lookup: ClusterHttpManagementRouteProvider.type = ClusterHttpManagementRouteProvider

  override def get(system: ActorSystem): ClusterHttpManagementRouteProvider = super.get(system)

  override def createExtension(system: ExtendedActorSystem): ClusterHttpManagementRouteProvider =
    new ClusterHttpManagementRouteProvider(system)

}

/**
 * Provides an HTTP management interface for [[akka.cluster.Cluster]].
 */
final class ClusterHttpManagementRouteProvider(system: ExtendedActorSystem) extends ManagementRouteProvider {

  private val cluster = Cluster(system)

  val settings: ClusterHttpManagementSettings = new ClusterHttpManagementSettings(system.settings.config)

  /** Routes to be exposed by Akka cluster management */
  override def routes(routeProviderSettings: ManagementRouteProviderSettings): Route =
    if (routeProviderSettings.readOnly) {
      ClusterHttpManagementRoutes.readOnly(cluster)
    } else {
      ClusterHttpManagementRoutes(cluster)
    }

}
