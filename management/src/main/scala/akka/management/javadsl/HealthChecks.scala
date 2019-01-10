/*
 * Copyright (C) 2017-2018 Lightbend Inc. <http://www.lightbend.com>
 */

package akka.management.javadsl
import java.util.concurrent.CompletionStage

import akka.actor.ExtendedActorSystem
import akka.dispatch.ExecutionContexts
import akka.management.HealthCheckSettings
import akka.management.internal.HealthChecksImpl

import scala.compat.java8.FutureConverters._

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
