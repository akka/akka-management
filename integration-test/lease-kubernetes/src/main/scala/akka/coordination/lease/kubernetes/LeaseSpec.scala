/*
 * Copyright (C) 2017-2025 Lightbend Inc. <https://akka.io>
 */

package akka.coordination.lease.kubernetes

import akka.actor.ActorSystem
import akka.coordination.lease.scaladsl.LeaseProvider
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.Eventually
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.Milliseconds
import org.scalatest.time.Seconds
import org.scalatest.time.Span
import org.scalatest.wordspec.AnyWordSpec

/**
 * This spec is for running inside a k8s cluster.
 */
abstract class LeaseSpec() extends AnyWordSpec with ScalaFutures with BeforeAndAfterAll with Matchers with Eventually {

  def system: ActorSystem

  implicit val patience: PatienceConfig = PatienceConfig(Span(3, Seconds), Span(500, Milliseconds))

  lazy val underTest = LeaseProvider(system)
  // for cleanup
  val config = system.settings.config.getConfig(KubernetesLease.configPath)
  val leaseName = "lease-1"
  val client1 = "client1"
  val client2 = "client2"

  // two leases instances for the same lease name
  lazy val lease1Client1 = underTest.getLease(leaseName, "akka.coordination.lease.kubernetes", client1)
  lazy val lease1Client2 = underTest.getLease(leaseName, "akka.coordination.lease.kubernetes", client2)

  "A lease" should {

    "be different instances" in {
      assert(lease1Client1 ne lease1Client2)
    }

    "work" in {
      lease1Client1.acquire().futureValue shouldEqual true
      lease1Client1.checkLease() shouldEqual true
    }

    "be reentrant" in {
      lease1Client1.acquire().futureValue shouldEqual true
      lease1Client1.checkLease() shouldEqual true
      lease1Client2.checkLease() shouldEqual false
    }

    "not allow another client to acquire the lease" in {
      lease1Client2.acquire().futureValue shouldEqual false
      lease1Client2.checkLease() shouldEqual false
    }

    "maintain the lease for a prolonged period" in {
      lease1Client1.acquire().futureValue shouldEqual true
      lease1Client1.checkLease() shouldEqual true
      Thread.sleep(200)
      lease1Client1.checkLease() shouldEqual true
      Thread.sleep(200)
      lease1Client1.checkLease() shouldEqual true
      Thread.sleep(200)
      lease1Client1.checkLease() shouldEqual true
    }

    "not allow another client to release the lease" in {
      lease1Client2.release().failed.futureValue.getMessage shouldEqual s"Tried to release a lease that is not acquired"
    }

    "allow removing the lease" in {
      lease1Client1.release().futureValue shouldEqual true
      eventually {
        lease1Client1.checkLease() shouldEqual false
      }
    }

    "allow a new client to get the lease once released" in {
      lease1Client2.acquire().futureValue shouldEqual true
      lease1Client2.checkLease() shouldEqual true
      lease1Client1.checkLease() shouldEqual false
    }
  }

}
