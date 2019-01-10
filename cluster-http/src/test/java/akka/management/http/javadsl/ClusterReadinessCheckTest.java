/*
 * Copyright (C) 2017-2018 Lightbend Inc. <http://www.lightbend.com>
 */

package akka.management.http.javadsl;

import akka.actor.ActorSystem;
import akka.management.cluster.javadsl.ClusterReadinessCheck;

import java.util.concurrent.CompletionStage;


public class ClusterReadinessCheckTest {

   private static ActorSystem system = null;

   // test type works
   public static CompletionStage<Boolean> worksFromJava() throws Exception {
      ClusterReadinessCheck check = new ClusterReadinessCheck(system);
      return check.get();
   }
}
