/*
 * Copyright (C) 2017-2025 Lightbend Inc. <https://akka.io>
 */

package akka.management.javadsl

import java.util.concurrent.CompletionStage
import java.util.function.Supplier
import java.util.function.{ Function => JFunction }
import java.util.{ Optional, List => JList }

import scala.jdk.FunctionConverters._
import scala.jdk.FutureConverters._

import akka.actor.ActorSystem
import akka.actor.ExtendedActorSystem
import akka.actor.setup.Setup
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
   * Returns CompletionStage(result), containing the system's readiness result
   */
  def readyResult(): CompletionStage[CheckResult] =
    delegate.readyResult().map(new CheckResult(_))(system.dispatcher).asJava

  /**
   * Returns CompletionStage(true) if the system is ready to receive user traffic
   */
  def ready(): CompletionStage[java.lang.Boolean] =
    readyResult().thenApply(((r: CheckResult) => r.isSuccess).asJava)

  /**
   * Returns CompletionStage(true) to indicate that the process is alive but does not
   * mean that it is ready to receive traffic e.g. is has not joined the cluster
   * or is loading initial state from a database
   */
  def aliveResult(): CompletionStage[CheckResult] =
    delegate.aliveResult().map(new CheckResult(_))(system.dispatcher).asJava

  /**
   * Returns CompletionStage(result) containing the system's liveness result
   */
  def alive(): CompletionStage[java.lang.Boolean] =
    aliveResult().thenApply(((r: CheckResult) => r.isSuccess).asJava)
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
 * Setup for readiness checks, constructor is *Internal API*, use factories in [[ReadinessCheckSetup]]
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
 * Setup for liveness checks, constructor is *Internal API*, use factories in [[LivenessCheckSetup]]
 */
final class LivenessCheckSetup private (
    val createHealthChecks: JFunction[ActorSystem, JList[Supplier[CompletionStage[java.lang.Boolean]]]]
) extends Setup

/**
 * Result for readiness and liveness checks
 */
final class CheckResult private[javadsl] (private val result: Either[String, Unit]) {
  def failure: Optional[String] =
    Optional.ofNullable(result.left.toOption.orNull)

  def isFailure: java.lang.Boolean = result.isLeft

  def isSuccess: java.lang.Boolean = result.isRight

  def success: Optional[Unit] =
    Optional.ofNullable(result.toOption.orNull)
}
