/*
 * Copyright (C) 2017 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.cluster.http.management;

import akka.actor.ActorSystem;
import akka.cluster.Cluster;
import akka.management.cluster.ClusterHttpManagement;
import org.junit.Test;

public class ClusterHttpManagementJavaCompileTest {

    public void test() {
        ActorSystem actorSystem = ActorSystem.create("test");
        ClusterHttpManagement x = ClusterHttpManagement.get(actorSystem);
    }

    @Test
    public void compileOnly() {}
}
