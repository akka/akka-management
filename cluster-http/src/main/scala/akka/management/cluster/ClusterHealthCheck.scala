/*
 * Copyright (C) 2017-2018 Lightbend Inc. <http://www.lightbend.com>
 */

package akka.management.cluster
import akka.actor.ExtendedActorSystem
import akka.cluster.Cluster
import akka.http.scaladsl.server.Route
import akka.management.http.{ ManagementRouteProvider, ManagementRouteProviderSettings }

final class ClusterHealthCheck(system: ExtendedActorSystem) extends ManagementRouteProvider {

  private val cluster = Cluster(system)

  override def routes(settings: ManagementRouteProviderSettings): Route = ClusterHealthCheckRoutes(cluster)
}
