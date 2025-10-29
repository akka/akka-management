/*
 * Copyright (C) 2017-2025 Lightbend Inc. <https://akka.io>
 */

package akka.management.cluster.bootstrap;

import akka.actor.ActorSystem;
import akka.management.cluster.bootstrap.ClusterBootstrap;
import org.junit.Test;

public class ClusterBootstrapJavaCompileTest {

  public void test() {
    ActorSystem actorSystem = ActorSystem.create("test");
    ClusterBootstrap clusterBootstrap = ClusterBootstrap.get(actorSystem);
  }

  @Test
  public void compileOnly() {
  }
}
