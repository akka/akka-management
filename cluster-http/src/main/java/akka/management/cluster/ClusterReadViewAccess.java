/*
 * Copyright (C) 2017-2018 Lightbend Inc. <http://www.lightbend.com>
 */

package akka.management.cluster;

import akka.annotation.InternalApi;
import akka.cluster.Cluster;
import akka.cluster.ClusterReadView;

/** INTERNAL API */
@InternalApi
public class ClusterReadViewAccess {

  /**
   * INTERNAL API
   *
   * Exposes the internal {@code readView} of the Akka Cluster, not reachable from Scala code because it is {@code private[cluster]}.
   */
  @InternalApi
  public static ClusterReadView internalReadView(Cluster cluster) {
    return cluster.readView();
  }
}
