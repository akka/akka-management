/*
 * Copyright (C) 2017-2018 Lightbend Inc. <http://www.lightbend.com>
 */

package akka.management.cluster

import akka.actor.{ ActorSystem, ExtendedActorSystem, ExtensionId, ExtensionIdProvider }
import akka.cluster.Cluster
import akka.http.scaladsl.server.Route
import akka.management.http.{ ManagementRouteProvider, ManagementRouteProviderSettings }

object ClusterHttpManagement extends ExtensionId[ClusterHttpManagement] with ExtensionIdProvider {
  override def lookup: ClusterHttpManagement.type = ClusterHttpManagement

  override def get(system: ActorSystem): ClusterHttpManagement = super.get(system)

  override def createExtension(system: ExtendedActorSystem): ClusterHttpManagement =
    new ClusterHttpManagement(system)

}

/**
 * Provides an HTTP management interface for [[akka.cluster.Cluster]].
 */
final class ClusterHttpManagement(system: ExtendedActorSystem) extends ManagementRouteProvider {

  private val cluster = Cluster(system)

  val settings: ClusterHttpManagementSettings = new ClusterHttpManagementSettings(system.settings.config)

  /** Routes to be exposed by Akka cluster management */
  override def routes(routeProviderSettings: ManagementRouteProviderSettings): Route =
    // ignore the settings, don't carry any information these routes need
    ClusterHttpManagementRoutes(cluster)

}
