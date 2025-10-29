/*
 * Copyright (C) 2017-2025 Lightbend Inc. <https://akka.io>
 */

package jdoc.akka.rollingupdate.kubernetes;

import akka.actor.ActorSystem;
import akka.rollingupdate.kubernetes.PodDeletionCost;

public class PodDeletionCostCompileOnly {
    public static void bootstrap() {

        ActorSystem system = ActorSystem.create();

        //#start
        // Starting the pod deletion cost annotator
        PodDeletionCost.get(system).start();
        //#start
    }
}
