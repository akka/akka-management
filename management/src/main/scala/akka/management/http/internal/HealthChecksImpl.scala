package akka.management.http.internal

import java.util.concurrent.CompletionStage

import akka.actor.ExtendedActorSystem
import akka.annotation.InternalApi
import akka.event.Logging
import akka.management.http.scaladsl.HealthChecks
import akka.management.http.{HealthCheckSettings, InvalidHealthCheckException}

import scala.collection.immutable
import scala.compat.java8.FutureConverters._
import scala.concurrent.Future
import scala.util.{Failure, Success}

/**
* INTERNAL API
  */
@InternalApi
private[akka] class HealthChecksImpl(system: ExtendedActorSystem,
                                     settings: HealthCheckSettings)
    extends HealthChecks {
  import HealthChecks._
  import system.dispatcher

  private val log = Logging(system, classOf[HealthChecksImpl])

  log.info("Loading readiness checks {}", settings.readinessChecks)
  log.info("Loading liveness checks {}", settings.livenessChecks)

  private val readiness: immutable.Seq[HealthCheck] = load(
    settings.readinessChecks
  )

  private val liveness: immutable.Seq[HealthCheck] = load(settings.livenessChecks)

  private def load(
    checks: immutable.Seq[String]
  ): immutable.Seq[HealthCheck] = {
    checks
      .map(
        fqcn =>
          system.dynamicAccess.createInstanceFor[HealthCheck](
            fqcn,
            immutable.Seq((classOf[ExtendedActorSystem], system))
        ).recoverWith {
            case _: ClassCastException =>
              system.dynamicAccess.createInstanceFor[java.util.function.Supplier[CompletionStage[Boolean]]](
                fqcn,
                immutable.Seq((classOf[ExtendedActorSystem], system))).map(sup => () => sup.get().toScala)
          }
      )
      .map {
        case Success(c) => c
        case Failure(_: NoSuchMethodException) =>
          throw new InvalidHealthCheckException(
            s"Health checks: [${checks.mkString(",")}] must have a single argument constructor that takes an ExtendedActorSystem"
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

  private def check(checks: immutable.Seq[HealthCheck]): Future[Boolean] = {
    Future.traverse(checks)(check => check()).map(_.forall(identity))
  }
}