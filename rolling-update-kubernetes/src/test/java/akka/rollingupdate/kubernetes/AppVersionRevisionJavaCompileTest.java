/*
 * Copyright (C) 2017-2024 Lightbend Inc. <https://www.lightbend.com>
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
