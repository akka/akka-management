/*
 * Copyright (C) 2017-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.management.http.javadsl;

import akka.actor.ActorSystem;
import akka.management.cluster.javadsl.ClusterMembershipCheck;

import java.util.concurrent.CompletionStage;


public class ClusterReadinessCheckTest {

   private static ActorSystem system = null;

   // test type works
   public static CompletionStage<Boolean> worksFromJava() throws Exception {
      ClusterMembershipCheck check = new ClusterMembershipCheck(system);
      return check.get();
   }
}
