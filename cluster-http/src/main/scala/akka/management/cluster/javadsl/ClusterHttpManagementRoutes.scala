/*
 * Copyright (C) 2017-2018 Lightbend Inc. <http://www.lightbend.com>
 */

package akka.management.cluster.javadsl
import akka.cluster.Cluster
import akka.http.javadsl.server.directives.RouteAdapter

object ClusterHttpManagementRoutes {

  /**
   * Creates an instance of [[ClusterHttpManagementRoutes]] to manage the specified
   * [[akka.cluster.Cluster]] instance. This version does not provide Basic Authentication. It uses
   * the specified path `pathPrefixName`.
   */
  def all(cluster: Cluster): akka.http.javadsl.server.Route =
    RouteAdapter(akka.management.cluster.ClusterHttpManagementRoutes(cluster))

  /**
   * Creates an instance of [[ClusterHttpManagementRoutes]] to manage the specified
   * [[akka.cluster.Cluster]] instance. This version does not provide Basic Authentication. It uses
   * the specified path `pathPrefixName`.
   */
  def readOnly(cluster: Cluster): akka.http.javadsl.server.Route =
    RouteAdapter(akka.management.cluster.ClusterHttpManagementRoutes.readOnly(cluster))

}
