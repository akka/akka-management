/*
 * Copyright (C) 2017-2018 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.management.javadsl

import java.util.concurrent.CompletionStage
import java.util.function.Supplier
import java.util.function.{ Function => JFunction }
import java.util.{ List => JList }

import scala.compat.java8.FutureConverters._

import akka.actor.ActorSystem
import akka.actor.ExtendedActorSystem
import akka.actor.setup.Setup
import akka.dispatch.ExecutionContexts
import akka.management.HealthCheckSettings
import akka.management.internal.HealthChecksImpl

/**
 * Can be used to instantiate health checks directly rather than rely on the
 * automatic management route. Useful if want to host the health check via
 * a protocol other than HTTP or not in the Akka Management HTTP server
 */
final class HealthChecks(system: ExtendedActorSystem, settings: HealthCheckSettings) {

  private val delegate = new HealthChecksImpl(system, settings)

  /**
   * Returns CompletionStage(true) if the system is ready to receive user traffic
   */
  def ready(): CompletionStage[java.lang.Boolean] =
    delegate.ready().map(Boolean.box)(ExecutionContexts.sameThreadExecutionContext).toJava

  /**
   * Returns CompletionStage(true) to indicate that the process is alive but does not
   * mean that it is ready to receive traffic e.g. is has not joined the cluster
   * or is loading initial state from a database
   */
  def alive(): CompletionStage[java.lang.Boolean] =
    delegate.alive().map(Boolean.box)(ExecutionContexts.sameThreadExecutionContext).toJava
}

object ReadinessCheckSetup {

  /**
   * Programmatic definition of readiness checks
   */
  def create(createHealthChecks: JFunction[ActorSystem, JList[Supplier[CompletionStage[java.lang.Boolean]]]])
    : ReadinessCheckSetup = {
    new ReadinessCheckSetup(createHealthChecks)
  }

}

/**
 * Setup for readiness checks, constructor is *Internal API*, use factories in [[ReadinessCheckSetup()]]
 */
final class ReadinessCheckSetup private (
    val createHealthChecks: JFunction[ActorSystem, JList[Supplier[CompletionStage[java.lang.Boolean]]]]
) extends Setup

object LivenessCheckSetup {

  /**
   * Programmatic definition of liveness checks
   */
  def create(createHealthChecks: JFunction[ActorSystem, JList[Supplier[CompletionStage[java.lang.Boolean]]]])
    : LivenessCheckSetup = {
    new LivenessCheckSetup(createHealthChecks)
  }

}

/**
 * Setup for liveness checks, constructor is *Internal API*, use factories in [[LivenessCheckSetup()]]
 */
final class LivenessCheckSetup private (
    val createHealthChecks: JFunction[ActorSystem, JList[Supplier[CompletionStage[java.lang.Boolean]]]]
) extends Setup
