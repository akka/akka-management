/*
 * Copyright (C) 2017-2023 Lightbend Inc. <https://www.lightbend.com>
 */

package jdoc.akka.rollingupdate.kubernetes;

import akka.actor.ActorSystem;
import akka.rollingupdate.kubernetes.AppVersionRevision;
public class AppVersionRevisionCompileOnly {
    public static void bootstrap() {

        ActorSystem system = ActorSystem.create();

        //#start
        // Starting the AppVersionRevision extension
        // preferred to be called before ClusterBootstrap
        AppVersionRevision.get(system).start();
        //#start
    }
}
