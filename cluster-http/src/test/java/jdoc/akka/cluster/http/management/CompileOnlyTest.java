/*
 * Copyright (C) 2017-2018 Lightbend Inc. <http://www.lightbend.com>
 */

package jdoc.akka.cluster.http.management;

import akka.actor.ActorSystem;
import akka.management.cluster.ClusterHttpManagement;

public class CompileOnlyTest {
    public static void example() {
        //#loading
        ActorSystem as = ActorSystem.create();
        ClusterHttpManagement httpMgmt = ClusterHttpManagement.get(as);
        //#loading
    }
}
