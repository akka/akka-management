/*
 * Copyright (C) 2017-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package jdoc.akka.cluster.http.management;

import akka.actor.ActorSystem;
import akka.management.scaladsl.AkkaManagement;
import akka.cluster.Cluster;
//#imports
import akka.http.javadsl.server.Route;
import akka.management.cluster.javadsl.ClusterHttpManagementRoutes;
//#imports

public class CompileOnlyTest {
    public static void example() {
        //#loading
        ActorSystem system = ActorSystem.create();
        AkkaManagement.get(system).start();
        //#loading


        //#all
        Cluster cluster = Cluster.get(system);
        Route allRoutes = ClusterHttpManagementRoutes.all(cluster);
        //#all

        //#read-only
        Route readOnlyRoutes = ClusterHttpManagementRoutes.readOnly(cluster);
        //#read-only
    }
}
