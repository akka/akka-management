/*
 * Copyright (C) 2017-2024 Lightbend Inc. <https://www.lightbend.com>
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
