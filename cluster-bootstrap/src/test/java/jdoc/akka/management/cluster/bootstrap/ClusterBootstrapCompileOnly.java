/*
 * Copyright (C) 2017-2018 Lightbend Inc. <https://www.lightbend.com>
 */

package jdoc.akka.management.cluster.bootstrap;

import akka.actor.ActorSystem;
import akka.management.scaladsl.AkkaManagement;
import akka.management.cluster.bootstrap.ClusterBootstrap;

public class ClusterBootstrapCompileOnly {
    public static void bootstrap() {

        ActorSystem system = ActorSystem.create();

        //#start
        // Akka Management hosts the HTTP routes used by bootstrap
        AkkaManagement.get(system).start();

        // Starting the bootstrap process needs to be done explicitly
        ClusterBootstrap.get(system).start();
        //#start
    }
}
