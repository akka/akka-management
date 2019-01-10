/*
 * Copyright (C) 2017-2018 Lightbend Inc. <http://www.lightbend.com>
 */

package akka.management

import akka.actor.{ ActorSystem, ExtendedActorSystem }
import akka.testkit.TestKit
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{ BeforeAndAfterAll, Matchers, WordSpecLike }
import HealthChecksSpec._
import akka.management.scaladsl.HealthChecks

import scala.concurrent.Future
import scala.collection.{ immutable => im }
import scala.util.control.NoStackTrace

object HealthChecksSpec {
  val failedCause = new TE()
  val ctxException = new TE()
}

class TE extends RuntimeException with NoStackTrace

class Ok(system: ActorSystem) extends (() => Future[Boolean]) {
  override def apply(): Future[Boolean] = {
    Future.successful(true)
  }
}

class False(system: ActorSystem) extends (() => Future[Boolean]) {
  override def apply(): Future[Boolean] = {
    Future.successful(false)
  }
}
class Throws(system: ActorSystem) extends (() => Future[Boolean]) {
  override def apply(): Future[Boolean] = {
    Future.failed(failedCause)
  }
}

class NoArgsCtr() extends (() => Future[Boolean]) {
  override def apply(): Future[Boolean] = ???
}

class WrongType() {}

class CtrException(system: ActorSystem) extends (() => Future[Boolean]) {
  override def apply(): Future[Boolean] = ???
  throw ctxException
}

class HealthChecksSpec
    extends TestKit(ActorSystem("HealthChecksSpec"))
    with WordSpecLike
    with BeforeAndAfterAll
    with ScalaFutures
    with Matchers {

  val eas = system.asInstanceOf[ExtendedActorSystem]

  override protected def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  def settings(readiness: im.Seq[String], liveness: im.Seq[String]) =
    new HealthCheckSettings(readiness, liveness, "ready", "alive")

  "HealthCheck" should {
    "succeed by default" in {
      val checks = HealthChecks(eas, settings(Nil, Nil))
      checks.alive().futureValue shouldEqual true
      checks.ready().futureValue shouldEqual true
    }
    "succeed for all health checks returning true" in {
      val checks = HealthChecks(
        eas,
        settings(
          im.Seq("akka.management.Ok"),
          im.Seq("akka.management.Ok")
        )
      )
      checks.alive().futureValue shouldEqual true
      checks.ready().futureValue shouldEqual true
    }
    "return false for health checks returning false" in {
      val checks = HealthChecks(
        eas,
        settings(
          im.Seq("akka.management.False"),
          im.Seq("akka.management.False")
        )
      )
      checks.ready().futureValue shouldEqual false
      checks.alive().futureValue shouldEqual false
    }
    "return failure for all health checks fail" in {
      val checks = HealthChecks(
        eas,
        settings(
          im.Seq("akka.management.Throws"),
          im.Seq("akka.management.Throws")
        )
      )
      checks.ready().failed.futureValue shouldEqual failedCause
      checks.alive().failed.futureValue shouldEqual failedCause
    }
    "return failure for if any of the checks fail" in {
      val checks = im.Seq(
        "akka.management.Ok",
        "akka.management.Throws",
        "akka.management.False"
      )
      val hc = HealthChecks(eas, settings(checks, checks))
      hc.ready().failed.futureValue shouldEqual failedCause
      hc.alive().failed.futureValue shouldEqual failedCause
    }
    "provide useful error if user's ctr is invalid" in {
      intercept[InvalidHealthCheckException] {
        val checks = im.Seq("akka.management.NoArgsCtr")
        HealthChecks(eas, settings(checks, checks))
      }.getMessage shouldEqual "Health checks: [akka.management.NoArgsCtr] must have a single argument constructor that takes an ActorSystem"
    }
    "provide useful error if invalid type" in {
      intercept[InvalidHealthCheckException] {
        val checks = im.Seq("akka.management.WrongType")
        HealthChecks(eas, settings(checks, checks))
      }.getMessage shouldEqual "Health checks: [akka.management.WrongType] must have type: () => Future[Boolean]"
    }
    "provide useful error if class not found" in {
      intercept[InvalidHealthCheckException] {
        val checks =
          im.Seq("akka.management.DoesNotExist", "akka.management.Ok")
        HealthChecks(eas, settings(checks, checks))
      }.getMessage shouldEqual "Health check: [akka.management.DoesNotExist] not found"
    }
    "provide useful error if class ctr throws" in {
      intercept[InvalidHealthCheckException] {
        val checks =
          im.Seq("akka.management.Ok", "akka.management.CtrException")
        HealthChecks(eas, settings(checks, checks))
      }.getCause shouldEqual ctxException
    }
  }
}
