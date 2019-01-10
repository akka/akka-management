/*
 * Copyright (C) 2017-2018 Lightbend Inc. <http://www.lightbend.com>
 */

package akka.management.internal

import java.util.concurrent.CompletionStage

import akka.actor.{ ActorSystem, ExtendedActorSystem }
import akka.annotation.InternalApi
import akka.event.Logging
import akka.management.{ HealthCheckSettings, InvalidHealthCheckException }
import akka.management.scaladsl.HealthChecks

import scala.collection.immutable
import scala.compat.java8.FutureConverters._
import scala.concurrent.Future
import scala.util.{ Failure, Success, Try }

final case class CheckTimeoutException(msg: String) extends RuntimeException(msg)

/**
 * INTERNAL API
 */
@InternalApi
final private[akka] class HealthChecksImpl(system: ExtendedActorSystem, settings: HealthCheckSettings)
    extends HealthChecks {
  import HealthChecks._
  import system.dispatcher

  private val log = Logging(system, classOf[HealthChecksImpl])

  log.debug("Loading readiness checks {}", settings.readinessChecks)
  log.debug("Loading liveness checks {}", settings.livenessChecks)

  private val readiness: immutable.Seq[HealthCheck] = load(
    settings.readinessChecks
  )

  private val liveness: immutable.Seq[HealthCheck] = load(
    settings.livenessChecks
  )

  private def tryLoadScalaHealthCheck(fqcn: String): Try[HealthCheck] = {
    system.dynamicAccess
      .createInstanceFor[HealthCheck](
        fqcn,
        immutable.Seq((classOf[ActorSystem], system))
      )
      .recoverWith {
        case _: NoSuchMethodException =>
          system.dynamicAccess.createInstanceFor[HealthCheck](fqcn, Nil)

      }
  }

  private def tryLoadJavaHealthCheck(fqcn: String): Try[HealthCheck] = {
    system.dynamicAccess
      .createInstanceFor[java.util.function.Supplier[CompletionStage[Boolean]]](
        fqcn,
        immutable.Seq((classOf[ActorSystem], system))
      )
      .recoverWith {
        case _: NoSuchMethodException =>
          system.dynamicAccess.createInstanceFor[java.util.function.Supplier[CompletionStage[
            Boolean
          ]]](fqcn, Nil)
      }
      .map(sup => () => sup.get().toScala)
  }

  private def load(
      checks: immutable.Seq[String]
  ): immutable.Seq[HealthCheck] = {
    checks
      .map(
        fqcn =>
          tryLoadScalaHealthCheck(fqcn).recoverWith {
            case _: ClassCastException =>
              tryLoadJavaHealthCheck(fqcn)
        }
      )
      .map {
        case Success(c) => c
        case Failure(_: NoSuchMethodException) =>
          throw new InvalidHealthCheckException(
            s"Health checks: [${checks.mkString(",")}] must have a no args constructor or a single argument constructor that takes an ActorSystem"
          )
        case Failure(_: ClassCastException) =>
          throw new InvalidHealthCheckException(
            s"Health checks: [${checks.mkString(",")}] must have type: () => Future[Boolean]"
          )
        case Failure(c: ClassNotFoundException) =>
          throw new InvalidHealthCheckException(
            s"Health check: [${c.getMessage}] not found"
          )
        case Failure(t) =>
          throw new InvalidHealthCheckException(
            "Uncaught exception from Health check construction",
            t
          )
      }
  }

  def ready(): Future[Boolean] = {
    check(readiness)
  }

  def alive(): Future[Boolean] = {
    check(liveness)
  }

  private def runCheck(check: HealthCheck): Future[Boolean] = {
    Future.fromTry(Try(check())).flatten
  }

  private def check(checks: immutable.Seq[HealthCheck]): Future[Boolean] = {
    val timeout = akka.pattern.after(settings.checkTimeout, system.scheduler)(
      Future.failed(
        CheckTimeoutException(s"Timeout after ${settings.checkTimeout}")
      )
    )
    Future.firstCompletedOf(
      Seq(
        Future.traverse(checks)(runCheck).map(_.forall(identity)),
        timeout
      )
    )
  }
}
