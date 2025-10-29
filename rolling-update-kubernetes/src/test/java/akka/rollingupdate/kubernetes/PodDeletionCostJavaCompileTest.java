/*
 * Copyright (C) 2017-2025 Lightbend Inc. <https://akka.io>
 */

package akka.rollingupdate.kubernetes;

import akka.actor.ActorSystem;
import akka.rollingupdate.kubernetes.PodDeletionCost;
import org.junit.Test;

public class PodDeletionCostJavaCompileTest {

  public void test() {
    ActorSystem actorSystem = ActorSystem.create("test");
    PodDeletionCost podDeletionCost = PodDeletionCost.get(actorSystem);
  }

  @Test
  public void compileOnly() {
  }
}
