/*
 * Copyright (C) 2017-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.management.cluster.javadsl
import akka.cluster.Cluster
import akka.http.javadsl.server.directives.RouteAdapter
import akka.management.cluster.scaladsl

object ClusterHttpManagementRoutes {

  /**
   * Creates an instance of [[ClusterHttpManagementRoutes]] to manage the specified
   * [[akka.cluster.Cluster]] instance. This version does not provide Basic Authentication. It uses
   * the specified path `pathPrefixName`.
   */
  def all(cluster: Cluster): akka.http.javadsl.server.Route =
    RouteAdapter(scaladsl.ClusterHttpManagementRoutes(cluster))

  /**
   * Creates an instance of [[ClusterHttpManagementRoutes]] to manage the specified
   * [[akka.cluster.Cluster]] instance. This version does not provide Basic Authentication. It uses
   * the specified path `pathPrefixName`.
   */
  def readOnly(cluster: Cluster): akka.http.javadsl.server.Route =
    RouteAdapter(scaladsl.ClusterHttpManagementRoutes.readOnly(cluster))

}
