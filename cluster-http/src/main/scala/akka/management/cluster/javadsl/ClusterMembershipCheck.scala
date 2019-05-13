/*
 * Copyright (C) 2017-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.management.cluster.javadsl

import java.util.concurrent.CompletionStage

import akka.management.cluster.scaladsl.{ ClusterMembershipCheck => ScalaClusterReadinessCheck }

import scala.compat.java8.FutureConverters._
import akka.actor.ActorSystem
import akka.dispatch.ExecutionContexts

class ClusterMembershipCheck(system: ActorSystem)
    extends java.util.function.Supplier[CompletionStage[java.lang.Boolean]] {

  private val delegate = new ScalaClusterReadinessCheck(system)

  override def get(): CompletionStage[java.lang.Boolean] = {
    delegate.apply().map(Boolean.box)(ExecutionContexts.sameThreadExecutionContext).toJava
  }
}
