/*
 * Copyright (C) 2017-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.coordination.lease.kubernetes

import akka.Done
import akka.actor.ActorSystem
import akka.coordination.lease.kubernetes.internal.KubernetesApiImpl
import akka.testkit.TestKit
import com.typesafe.config.ConfigFactory
import org.scalatest.{ BeforeAndAfterAll, CancelAfterFailure }
import org.scalatest.concurrent.{ Eventually, ScalaFutures }
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.{ Await, Future }
import scala.concurrent.duration._

/**
 * This test requires an API server available on localhost:8080, the lease CRD created and a namespace called lease
 *
 * One way of doing this is to have a kubectl proxy open:
 *
 * `kubectl proxy --port=8080`
 *
 */
class KubernetesApiIntegrationTest
    extends TestKit(
      ActorSystem(
        "KubernetesApiIntegrationSpec",
        ConfigFactory.parseString("""
    akka.loglevel = DEBUG
    akka.coordination.lease.kubernetes.lease-operation-timeout = 1.5s
    """)
      ))
    with AnyWordSpecLike
    with Matchers
    with ScalaFutures
    with BeforeAndAfterAll
    with CancelAfterFailure
    with Eventually {

  implicit val patience: PatienceConfig = PatienceConfig(testKitSettings.DefaultTimeout.duration)

  val settings = new KubernetesSettings(
    "",
    "",
    "localhost",
    8080,
    10.minutes,
    namespace = Some("lease"),
    "",
    apiServerRequestTimeout = 1.second,
    false
  )

  val underTest = new KubernetesApiImpl(system, settings, "lease", () => Future.successful("token"), None)
  val leaseName = "lease-1"
  val client1 = "client-1"
  val client2 = "client-2"
  var currentVersion = ""

  override protected def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  override protected def beforeAll(): Unit = {
    // do some operation to check the proxy is up
    eventually {
      Await.result(underTest.removeLease(leaseName), 2.second) shouldEqual Done
    }
  }

  "Kubernetes lease resource" should {
    "be able to be created" in {
      val leaseRecord = underTest.readOrCreateLeaseResource(leaseName).futureValue
      leaseRecord.owner shouldEqual None
      leaseRecord.version shouldNot equal("")
      leaseRecord.version shouldNot equal(null)
      currentVersion = leaseRecord.version
    }

    "be able to read back with same version" in {
      val leaseRecord = underTest.readOrCreateLeaseResource(leaseName).futureValue
      leaseRecord.version shouldEqual currentVersion
    }

    "be able to take a lease with no owner" in {
      val leaseRecord = underTest.updateLeaseResource(leaseName, client1, currentVersion).futureValue
      val success: LeaseResource = leaseRecord match {
        case Right(lr) => lr
        case Left(_)   => fail("There shouldn't be anyone else updating the resource.")
      }
      success.version shouldNot equal(currentVersion)
      currentVersion = success.version
      success.owner shouldEqual Some(client1)
    }

    "be able to update a lease if resource version is correct" in {
      val timeUpdate = System.currentTimeMillis()
      val leaseRecord = underTest.updateLeaseResource(leaseName, client1, currentVersion, time = timeUpdate).futureValue
      val success: LeaseResource = leaseRecord match {
        case Right(lr) => lr
        case Left(_)   => fail("There shouldn't be anyone else updating the resource.")
      }
      success.version shouldNot equal(currentVersion)
      currentVersion = success.version
      success.owner shouldEqual Some(client1)
      success.time shouldEqual timeUpdate
    }

    "not be able to update a lease if resource version is correct" in {
      val timeUpdate = System.currentTimeMillis()
      val leaseRecord = underTest.updateLeaseResource(leaseName, client1, "10", time = timeUpdate).futureValue
      val failure: LeaseResource = leaseRecord match {
        case Right(_) => fail("Expected update failure (we've used an invalid version!).")
        case Left(lr) => lr
      }
      failure.version shouldEqual currentVersion
      currentVersion = failure.version
      failure.owner shouldEqual Some(client1)
      failure.time shouldNot equal(timeUpdate)
    }

    "be able to remove ownership" in {
      val leaseRecord = underTest.updateLeaseResource(leaseName, "", currentVersion).futureValue
      val success: LeaseResource = leaseRecord match {
        case Right(lr) => lr
        case Left(_)   => fail("There shouldn't be anyone else updating the resource.")
      }
      success.version shouldNot equal(currentVersion)
      currentVersion = success.version
      success.owner shouldEqual None
    }

    "be able to get lease once other client has removed" in {
      val leaseRecord = underTest.updateLeaseResource(leaseName, client2, currentVersion).futureValue
      val success: LeaseResource = leaseRecord match {
        case Right(lr) => lr
        case Left(_)   => fail("There shouldn't be anyone else updating the resource.")
      }
      success.version shouldNot equal(currentVersion)
      currentVersion = success.version
      success.owner shouldEqual Some(client2)
    }
  }

}
