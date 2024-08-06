/*
 * Copyright (C) 2017-2024 Lightbend Inc. <https://www.lightbend.com>
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
