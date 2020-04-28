/*
 * Copyright (C) 2017-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.management

import akka.actor.{ ActorSystem, ExtendedActorSystem }
import akka.testkit.TestKit
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{ BeforeAndAfterAll, Matchers, WordSpecLike }
import HealthChecksSpec._
import akka.management.internal.CheckTimeoutException
import akka.management.scaladsl.HealthChecks
import scala.concurrent.{ Await, Future }
import scala.collection.{ immutable => im }
import scala.util.control.NoStackTrace
import scala.concurrent.duration._

import akka.actor.BootstrapSetup
import akka.actor.setup.ActorSystemSetup
import akka.management.scaladsl.LivenessCheckSetup
import akka.management.scaladsl.ReadinessCheckSetup
import com.typesafe.config.ConfigFactory

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
  override def apply(): Future[Boolean] = {
    Future.successful(true)
  }
}

class InvalidCtr(cat: String) extends (() => Future[Boolean]) {
  override def apply(): Future[Boolean] = {
    Future.successful(true)
  }
}

class Slow(system: ActorSystem) extends (() => Future[Boolean]) {
  override def apply(): Future[Boolean] = {
    import scala.concurrent.ExecutionContext.Implicits.global
    Future {
      Thread.sleep(20000)
      false
    }
  }
}

class Naughty() extends (() => Future[Boolean]) {
  override def apply(): Future[Boolean] = {
    throw new RuntimeException("bad")
  }
}

class WrongType() {}

class CtrException(system: ActorSystem) extends (() => Future[Boolean]) {
  override def apply(): Future[Boolean] = ???
  if (System.currentTimeMillis() != -1) throw ctxException // avoid compiler warnign
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

  val OkCheck = NamedHealthCheck("Ok", "akka.management.Ok")
  val FalseCheck = NamedHealthCheck("False", "akka.management.False")
  val ThrowsCheck = NamedHealthCheck("Throws", "akka.management.Throws")
  val SlowCheck = NamedHealthCheck("Slow", "akka.management.Slow")
  val NoArgsCtrCheck = NamedHealthCheck("NoArgsCtr", "akka.management.NoArgsCtr")
  val NaughtyCheck = NamedHealthCheck("Naughty", "akka.management.Naughty")
  val InvalidCtrCheck = NamedHealthCheck("InvalidCtr", "akka.management.InvalidCtr")
  val WrongTypeCheck = NamedHealthCheck("WrongType", "akka.management.WrongType")
  val DoesNotExist = NamedHealthCheck("DoesNotExist", "akka.management.DoesNotExist")
  val CtrExceptionCheck = NamedHealthCheck("CtrExceptionCheck", "akka.management.CtrException")

  def settings(readiness: im.Seq[NamedHealthCheck], liveness: im.Seq[NamedHealthCheck]) =
    new HealthCheckSettings(readiness, liveness, "ready", "alive", 500.millis)

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
          im.Seq(OkCheck),
          im.Seq(OkCheck)
        )
      )
      checks.alive().futureValue shouldEqual true
      checks.ready().futureValue shouldEqual true
    }
    "support no args constructor" in {
      val checks = HealthChecks(
        eas,
        settings(
          im.Seq(NoArgsCtrCheck),
          im.Seq(NoArgsCtrCheck)
        )
      )
      checks.alive().futureValue shouldEqual true
      checks.ready().futureValue shouldEqual true
    }
    "return false for health checks returning false" in {
      val checks = HealthChecks(
        eas,
        settings(
          im.Seq(FalseCheck),
          im.Seq(FalseCheck)
        )
      )
      checks.ready().futureValue shouldEqual false
      checks.alive().futureValue shouldEqual false
    }
    "return failure for all health checks fail" in {
      val checks = HealthChecks(
        eas,
        settings(
          im.Seq(ThrowsCheck),
          im.Seq(ThrowsCheck)
        )
      )
      checks.ready().failed.futureValue shouldEqual failedCause
      checks.alive().failed.futureValue shouldEqual failedCause
    }
    "return failure if any of the checks fail" in {
      val checks = im.Seq(
        OkCheck,
        ThrowsCheck,
        FalseCheck
      )
      val hc = HealthChecks(eas, settings(checks, checks))
      hc.ready().failed.futureValue shouldEqual failedCause
      hc.alive().failed.futureValue shouldEqual failedCause
    }
    "return failure if check throws" in {
      val checks = im.Seq(
        NaughtyCheck
      )
      val hc = HealthChecks(eas, settings(checks, checks))
      hc.ready().failed.futureValue.getMessage shouldEqual "bad"
      hc.alive().failed.futureValue.getMessage shouldEqual "bad"
    }
    "return failure if checks timeout" in {
      val checks = im.Seq(
        SlowCheck,
        OkCheck
      )
      val hc = HealthChecks(eas, settings(checks, checks))
      Await.result(hc.ready().failed, 1.second) shouldEqual CheckTimeoutException("Timeout after 500 milliseconds")
      Await.result(hc.alive().failed, 1.second) shouldEqual CheckTimeoutException("Timeout after 500 milliseconds")
    }
    "provide useful error if user's ctr is invalid" in {
      intercept[InvalidHealthCheckException] {
        val checks = im.Seq(InvalidCtrCheck)
        HealthChecks(eas, settings(checks, checks))
      }.getMessage shouldEqual "Health checks: [NamedHealthCheck(InvalidCtr,akka.management.InvalidCtr)] must have a no args constructor or a single argument constructor that takes an ActorSystem"
    }
    "provide useful error if invalid type" in {
      intercept[InvalidHealthCheckException] {
        val checks = im.Seq(WrongTypeCheck)
        HealthChecks(eas, settings(checks, checks))
      }.getMessage shouldEqual "Health checks: [NamedHealthCheck(WrongType,akka.management.WrongType)] must have type: () => Future[Boolean]"
    }
    "provide useful error if class not found" in {
      intercept[InvalidHealthCheckException] {
        val checks =
          im.Seq(DoesNotExist, OkCheck)
        HealthChecks(eas, settings(checks, checks))
      }.getMessage shouldEqual "Health check: [akka.management.DoesNotExist] not found"
    }
    "provide useful error if class ctr throws" in {
      intercept[InvalidHealthCheckException] {
        val checks =
          im.Seq(OkCheck, CtrExceptionCheck)
        HealthChecks(eas, settings(checks, checks))
      }.getCause shouldEqual ctxException
    }
    "be possible to define via ActorSystem Setup" in {
      val readinessSetup = ReadinessCheckSetup(system => List(new Ok(system), new False(system)))
      val livenessSetup = LivenessCheckSetup(system => List(new False(system)))
      // bootstrapSetup is needed for config (otherwise default config)
      val bootstrapSetup = BootstrapSetup(ConfigFactory.parseString("some=thing"))
      val actorSystemSetup = ActorSystemSetup(bootstrapSetup, readinessSetup, livenessSetup)
      val sys2 = ActorSystem("HealthCheckSpec2", actorSystemSetup).asInstanceOf[ExtendedActorSystem]
      try {
        val checks = HealthChecks(
          sys2,
          settings(Nil, Nil) // no checks from config
        )
        checks.alive().futureValue shouldEqual false
        checks.ready().futureValue shouldEqual false
      } finally {
        TestKit.shutdownActorSystem(sys2)
      }
    }
  }
}
