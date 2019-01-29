/*
 * Copyright (C) 2017-2018 Lightbend Inc. <http://www.lightbend.com>
 */

package akka.management.internal

import java.util.concurrent.CompletionStage

import akka.actor.{ ActorSystem, ExtendedActorSystem }
import akka.annotation.InternalApi
import akka.event.Logging
import akka.management.{ HealthCheckSettings, InvalidHealthCheckException, NamedHealthCheck }
import akka.management.scaladsl.HealthChecks
import scala.collection.immutable
import scala.compat.java8.FutureConverters._
import scala.concurrent.Future
import scala.util.{ Failure, Success, Try }

import akka.management.scaladsl.LivenessCheckSetup
import akka.management.scaladsl.ReadinessCheckSetup

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

  log.info("Loading readiness checks {}", settings.readinessChecks)
  log.info("Loading liveness checks {}", settings.livenessChecks)

  private val readiness: immutable.Seq[HealthCheck] = {
    val fromSetup = system.settings.setup.get[ReadinessCheckSetup] match {
      case None => Nil
      case Some(setup) => setup.createHealthChecks(system)
    }
    val fromConfig = load(settings.readinessChecks)
    fromConfig ++ fromSetup
  }

  private val liveness: immutable.Seq[HealthCheck] = {
    val fromSetup = system.settings.setup.get[LivenessCheckSetup] match {
      case None => Nil
      case Some(setup) => setup.createHealthChecks(system)
    }
    val fromConfig = load(settings.livenessChecks)
    fromConfig ++ fromSetup
  }

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
      checks: immutable.Seq[NamedHealthCheck]
  ): immutable.Seq[HealthCheck] = {
    checks
      .map(
        namedHealthCheck =>
          tryLoadScalaHealthCheck(namedHealthCheck.fullyQualifiedClassName).recoverWith {
            case _: ClassCastException =>
              tryLoadJavaHealthCheck(namedHealthCheck.fullyQualifiedClassName)
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
    Future.fromTry(Try(check())).flatMap(identity)
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
