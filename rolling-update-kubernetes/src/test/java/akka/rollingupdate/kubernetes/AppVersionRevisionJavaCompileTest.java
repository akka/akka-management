/*
 * Copyright (C) 2017-2025 Lightbend Inc. <https://akka.io>
 */

package akka.rollingupdate.kubernetes;

import akka.actor.ActorSystem;
import org.junit.Test;

public class AppVersionRevisionJavaCompileTest {

  public void test() {
    ActorSystem actorSystem = ActorSystem.create("test");
    AppVersionRevision appVersionRevision = AppVersionRevision.get(actorSystem);
  }

  @Test
  public void compileOnly() {
  }
}
