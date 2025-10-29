/*
 * Copyright (C) 2017-2025 Lightbend Inc. <https://akka.io>
 */

package akka.management.cluster.javadsl

import java.util.concurrent.CompletionStage

import akka.management.cluster.scaladsl.{ ClusterMembershipCheck => ScalaClusterReadinessCheck }

import scala.concurrent.ExecutionContext
import scala.jdk.FutureConverters._

import akka.actor.ActorSystem

class ClusterMembershipCheck(system: ActorSystem)
    extends java.util.function.Supplier[CompletionStage[java.lang.Boolean]] {

  private val delegate = new ScalaClusterReadinessCheck(system)

  override def get(): CompletionStage[java.lang.Boolean] = {
    delegate.apply().map(Boolean.box)(ExecutionContext.parasitic).asJava
  }
}
