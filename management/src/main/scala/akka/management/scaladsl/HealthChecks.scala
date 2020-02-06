/*
 * Copyright (C) 2017-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.management.scaladsl
import scala.collection.immutable
import scala.concurrent.Future

import akka.actor.ActorSystem
import akka.actor.ExtendedActorSystem
import akka.actor.setup.Setup
import akka.annotation.DoNotInherit
import akka.management.HealthCheckSettings
import akka.management.internal.HealthChecksImpl

/**
 * Loads health checks from configuration and ActorSystem Setup
 */
object HealthChecks {
  def apply(system: ExtendedActorSystem, settings: HealthCheckSettings): HealthChecks =
    new HealthChecksImpl(system, settings)

  type HealthCheck = () => Future[Boolean]

}

/**
 * Not for user extension
 */
@DoNotInherit
abstract class HealthChecks {

  /**
   * Returns Future(true) if the system is ready to receive user traffic
   */
  def ready(): Future[Boolean]

  /**
   * Returns Future(result) containing the system's readiness result
   */
  def readyResult(): Future[Either[String, Unit]]

  /**
   * Returns Future(true) to indicate that the process is alive but does not
   * mean that it is ready to receive traffic e.g. is has not joined the cluster
   * or is loading initial state from a database
   */
  def alive(): Future[Boolean]

  /**
   * Returns Future(result) containing the system's liveness result
   */
  def aliveResult(): Future[Either[String, Unit]]
}

object ReadinessCheckSetup {

  /**
   * Programmatic definition of readiness checks
   */
  def apply(createHealthChecks: ActorSystem => immutable.Seq[HealthChecks.HealthCheck]): ReadinessCheckSetup = {
    new ReadinessCheckSetup(createHealthChecks)
  }

}

/**
 * Setup for readiness checks, constructor is *Internal API*, use factories in [[ReadinessCheckSetup()]]
 */
final class ReadinessCheckSetup private (
    val createHealthChecks: ActorSystem => immutable.Seq[HealthChecks.HealthCheck]
) extends Setup

object LivenessCheckSetup {

  /**
   * Programmatic definition of liveness checks
   */
  def apply(createHealthChecks: ActorSystem => immutable.Seq[HealthChecks.HealthCheck]): LivenessCheckSetup = {
    new LivenessCheckSetup(createHealthChecks)
  }

}

/**
 * Setup for liveness checks, constructor is *Internal API*, use factories in [[LivenessCheckSetup()]]
 */
final class LivenessCheckSetup private (
    val createHealthChecks: ActorSystem => immutable.Seq[HealthChecks.HealthCheck]
) extends Setup
