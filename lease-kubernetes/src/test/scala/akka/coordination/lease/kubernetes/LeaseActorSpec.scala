/*
 * Copyright (C) 2017-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.coordination.lease.kubernetes

import java.util.concurrent.atomic.AtomicBoolean

import akka.actor.Status.Failure
import akka.actor.{ ActorRef, ActorSystem }
import akka.coordination.lease.kubernetes.LeaseActor._
import akka.coordination.lease.{ LeaseException, LeaseSettings, TimeoutSettings }
import akka.pattern.ask
import akka.testkit.{ TestKit, TestProbe }
import akka.util.{ ConstantFun, Timeout }
import com.typesafe.config.ConfigFactory
import org.scalatest.BeforeAndAfterAll

import scala.concurrent.Future
import scala.concurrent.duration._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

class MockKubernetesApi(system: ActorSystem, currentLease: ActorRef, updateLease: ActorRef) extends KubernetesApi {

  implicit val timeout: Timeout = Timeout(10.seconds)

  override def readOrCreateLeaseResource(name: String): Future[LeaseResource] = {
    currentLease.ask(name).mapTo[LeaseResource]
  }

  override def updateLeaseResource(
      leaseName: String,
      clientName: String,
      version: String,
      time: Long): Future[Either[LeaseResource, LeaseResource]] = {
    updateLease.ask((clientName, version)).mapTo[Either[LeaseResource, LeaseResource]]
  }
}

class LeaseActorSpec
    extends TestKit(
      ActorSystem(
        "LeaseActorSpec",
        ConfigFactory.parseString("""
    akka.loggers = []
    akka.loglevel = DEBUG
    akka.stdout-loglevel = DEBUG
    akka.actor.debug.fsm = true
  """)
      ))
    with AnyWordSpecLike
    with Matchers
    with BeforeAndAfterAll {

  override protected def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  val leaseName = "sbr"

  "LeaseActor" should {

    // TODO what if the same client asks for the lease when granting? respond to both or ignore?

    "acquire empty lease" in new Test {
      underTest ! LeaseActor.Acquire()
      leaseProbe.expectMsg(leaseName)
      leaseProbe.reply(LeaseResource(None, currentVersion, System.currentTimeMillis()))

      // as no one owns the lock get the lock
      updateProbe.expectMsg((ownerName, currentVersion))
      incrementVersion()
      updateProbe.reply(Right(LeaseResource(Some(ownerName), currentVersion, System.currentTimeMillis())))
      senderProbe.expectMsg(LeaseAcquired)
    }

    "handle failure in initial read" in new Test {
      val k8sApiFailure = new LeaseException("Failed to communicate with API server")
      underTest ! LeaseActor.Acquire()
      leaseProbe.expectMsg(leaseName)
      leaseProbe.reply(Failure(k8sApiFailure))
      senderProbe.expectMsg(Failure(k8sApiFailure))
    }

    "allow acquire after initial failure on rad" in new Test {
      k8sApiFailureDuringRead()
      acquireLease()
    }

    "allow client to re-acquire the same lease" in new Test {
      acquireLease()
      underTest ! Acquire()
      senderProbe.expectMsg(LeaseAcquired)
    }

    "fail if grating takes longer than the heartbeat timeout" in new Test {
      underTest ! LeaseActor.Acquire()
      leaseProbe.expectMsg(leaseName)
      leaseProbe.reply(LeaseResource(None, currentVersion, System.currentTimeMillis()))
      updateProbe.expectMsg((ownerName, currentVersion))

      // too slow, could have already timed out
      updateProbe.expectNoMessage(leaseSettings.timeoutSettings.heartbeatTimeout * 2)
      incrementVersion()
      updateProbe.reply(Right(LeaseResource(Some(ownerName), currentVersion, System.currentTimeMillis())))
      // not granted
      senderProbe.expectMsgType[Failure].cause.getMessage should startWith("API server took too long to respond")
      granted.get() shouldEqual false

      // should allow retry
      acquireLease()

    }

    // FIXME, give up if API server is constantly slow to respond

    "reject taken lease in state idle" in new Test {
      underTest ! LeaseActor.Acquire()
      leaseProbe.expectMsg(leaseName)
      leaseProbe.reply(LeaseResource(Some("a different client"), currentVersion, System.currentTimeMillis()))
      senderProbe.expectMsg(LeaseTaken)
    }

    "heartbeat granted lease" in new Test {
      acquireLease()

      updateProbe.expectMsg((ownerName, currentVersion))
      incrementVersion()
      updateProbe.reply(Right(LeaseResource(Some(ownerName), currentVersion, System.currentTimeMillis())))
      updateProbe.expectMsg((ownerName, currentVersion))

      incrementVersion()
      updateProbe.reply(Right(LeaseResource(Some(ownerName), currentVersion, System.currentTimeMillis())))
      updateProbe.expectMsg((ownerName, currentVersion))
    }

    "remove lease from k8s when released" in new Test {
      acquireLease()
      underTest ! Release()
      updateProbe.expectMsg(("", currentVersion))
    }

    "remove lease from k8s conflict during update but lease has removed" in new Test {
      // "should not happen TM"
      acquireLease()
      underTest ! Release()
      updateProbe.expectMsg(("", currentVersion))
      incrementVersion()
      updateProbe.reply(Left(LeaseResource(None, currentVersion, System.currentTimeMillis())))
      senderProbe.expectMsg(LeaseReleased)
    }

    "remove lease from k8s conflict during update but lease taken by another" in new Test {
      // "should not happen TM"
      acquireLease()
      underTest ! Release()
      updateProbe.expectMsg(("", currentVersion))
      incrementVersion()
      updateProbe.reply(Left(LeaseResource(Some("another client"), currentVersion, System.currentTimeMillis())))
      senderProbe.expectMsg(LeaseReleased)
    }

    "remove lease from k8s failure" in new Test {
      val k8sApiFailure = new LeaseException("Failed to communicate with API server")
      acquireLease()
      underTest ! Release()
      updateProbe.expectMsg(("", currentVersion))
      incrementVersion()
      updateProbe.reply(Failure(k8sApiFailure))
      senderProbe.expectMsg(Failure(k8sApiFailure))
    }

    "sets granted when granted" in new Test {
      granted.get shouldEqual false
      acquireLease()
      awaitAssert {
        granted.get shouldEqual true
      }
    }

    "sets granted when acquired and released" in new Test {
      granted.get shouldEqual false
      acquireLease()
      awaitAssert {
        granted.get === true
      }
      releaseLease()
      awaitAssert {
        granted.get === false
      }
    }

    "released lock should be acquireable" in new Test {
      acquireLease()
      releaseLease()
      // Version from the previous lock so can skip the read of the resource unless the CAS fails
      acquireLeaseWithoutRead(ownerName)
    }

    "released lock acquired with new version" in new Test {
      acquireLease()
      releaseLease()

      // Version from the previous lock so can skip the read of the resource unless the CAS fails
      underTest ! LeaseActor.Acquire()
      updateProbe.expectMsg((ownerName, currentVersion))
      // Fail due to cas, version has moved on by 6 but no one owns the lock
      val failedVersion = currentVersionCount + 6
      updateProbe.reply(Left(LeaseResource(None, failedVersion.toString, System.currentTimeMillis())))
      // Try again
      updateProbe.expectMsg((ownerName, failedVersion.toString))
      updateProbe.reply(Right(LeaseResource(Some(ownerName), failedVersion.toString, System.currentTimeMillis())))
      senderProbe.expectMsg(LeaseAcquired)
    }

    "heartbeat conflict should set granted to false" in new Test {
      acquireLease()
      expectHeartBeat()
      granted.get() shouldEqual true

      updateProbe.expectMsg((ownerName, currentVersion))
      incrementVersion()
      updateProbe.reply(Left(LeaseResource(Some("i stole your lock"), currentVersion, System.currentTimeMillis())))
      awaitAssert {
        granted.get() shouldEqual false
      }
    }

    "heartbeat conflict should call lease lost callback" in new Test {
      @volatile var callbackCalled: Option[Throwable] = _
      acquireLease(reason => callbackCalled = reason)
      expectHeartBeat()
      granted.get() shouldEqual true

      updateProbe.expectMsg((ownerName, currentVersion))
      incrementVersion()
      updateProbe.reply(Left(LeaseResource(Some("i stole your lock"), currentVersion, System.currentTimeMillis())))
      awaitAssert {
        callbackCalled shouldEqual None
      }
    }

    "heartbeat fail should set granted to false" in new Test {
      val k8sApiFailure = new LeaseException("Failed to communicate with API server")
      acquireLease()
      expectHeartBeat()
      granted.get() shouldEqual true

      updateProbe.expectMsg((ownerName, currentVersion))
      incrementVersion()
      updateProbe.reply(Failure(k8sApiFailure))
      awaitAssert {
        granted.get() shouldEqual false
      }
    }

    "heartbeat fail should call lease lost callback" in new Test {
      val k8sApiFailure = new LeaseException("Failed to communicate with API server")
      @volatile var callbackCalled: Option[Throwable] = _
      acquireLease(reason => callbackCalled = reason)
      expectHeartBeat()
      granted.get() shouldEqual true

      updateProbe.expectMsg((ownerName, currentVersion))
      incrementVersion()
      updateProbe.reply(Failure(k8sApiFailure))
      awaitAssert {
        callbackCalled shouldEqual Some(k8sApiFailure)
      }
    }

    "lock should be acquireable after heart beat conflict" in new Test {
      acquireLease()
      expectHeartBeat()
      heartBeatConflict()
      acquireLease()
    }

    "lock should be acquireable after heart beat fail" in new Test {
      acquireLease()
      expectHeartBeat()
      heartBeatFailure()
      acquireLease()
    }

    "lease acquire in reading state" in new Test {
      // TODO this could accumulate senders and reply to all, atm it'll log saying
      // previous action hasn't finished
      pending
    }

    "return lease taken if conflict when updating lease" in new Test {
      underTest ! LeaseActor.Acquire()
      leaseProbe.expectMsg(leaseName)
      leaseProbe.reply(LeaseResource(None, currentVersion, System.currentTimeMillis()))
      updateProbe.expectMsg((ownerName, currentVersion))
      incrementVersion()
      updateProbe.reply(Left(LeaseResource(Some("some one else :("), currentVersion, System.currentTimeMillis())))
      senderProbe.expectMsg(LeaseTaken)
    }

    "be able to get lease after failing previous grant update" in new Test {
      failToGetLeaseDuringGrantingUpdate()
      acquireLease()
    }

    "allow lease to be overwritten if TTL expired (from IDLE state, need version read)" in new Test {
      val crashedClient = "crashedClient"

      underTest ! LeaseActor.Acquire()
      leaseProbe.expectMsg(leaseName)
      // lease is now older than the timeout, ahhhh
      leaseProbe.reply(
        LeaseResource(
          Some(crashedClient),
          currentVersion,
          System.currentTimeMillis() - (leaseSettings.timeoutSettings.heartbeatTimeout.toMillis * 2)))
      // try and get the lease
      updateProbe.expectMsg((ownerName, currentVersion))
      incrementVersion()
      updateProbe.reply(Right(LeaseResource(Some(ownerName), currentVersion, System.currentTimeMillis())))
      senderProbe.expectMsg(LeaseAcquired)
    }

    "allow lease to be overwritten if TTL expired (after previous failed attempt)" in new Test {
      val crashedClient = "crashedClient"
      failToGetTakenLease(crashedClient)
      // Second try the TTL is reached
      underTest ! LeaseActor.Acquire()
      leaseProbe.expectMsg(leaseName)
      // lease is now older than the timeout, ahhhh
      leaseProbe.reply(
        LeaseResource(
          Some(crashedClient),
          currentVersion,
          System.currentTimeMillis() - (leaseSettings.timeoutSettings.heartbeatTimeout.toMillis * 2)))
      // try and get the lease
      updateProbe.expectMsg((ownerName, currentVersion))
      incrementVersion()
      updateProbe.reply(Right(LeaseResource(Some(ownerName), currentVersion, System.currentTimeMillis())))
      senderProbe.expectMsg(LeaseAcquired)
    }

    // If we crash and then come back and read our own client name back AND it hasn't timed out
    "allow lease to be taken if owned by same client name from IDLE" in new Test {
      underTest.tell(LeaseActor.Acquire(), senderProbe.ref)
      leaseProbe.expectMsg(leaseName)
      leaseProbe.reply(LeaseResource(Some(ownerName), currentVersion, System.currentTimeMillis()))
      updateProbe.expectNoMessage(leaseSettings.timeoutSettings.heartbeatInterval / 2) // no time update required
      senderProbe.expectMsg(LeaseAcquired)
      expectHeartBeat()
    }

    // If we crash and read our own client name back and it has timed out it needs a time update
    // in this case another node could be trying to get the lease so we should go through
    // the full granting process
    "renew time if lease is owned by client on initial acquire" in new Test {
      underTest.tell(LeaseActor.Acquire(), senderProbe.ref)
      leaseProbe.expectMsg(leaseName)
      leaseProbe.reply(
        LeaseResource(
          Some(ownerName),
          currentVersion,
          System.currentTimeMillis() - (leaseSettings.timeoutSettings.heartbeatTimeout.toMillis * 2)))
      senderProbe.expectNoMessage(leaseSettings.timeoutSettings.heartbeatTimeout / 3) // not grated yet
      updateProbe.expectMsg((ownerName, currentVersion)) // update time
      incrementVersion()
      updateProbe.reply(Right(LeaseResource(Some(ownerName), currentVersion, System.currentTimeMillis())))
      senderProbe.expectMsg(LeaseAcquired)
      expectHeartBeat()
    }

  }

  trait Test {
    val ownerName = "owner1"
    val leaseSettings: LeaseSettings = new LeaseSettings(
      leaseName,
      ownerName,
      new TimeoutSettings(25.millis, 250.millis, 1.second),
      ConfigFactory.empty())

    var currentVersionCount = 1
    def currentVersion = currentVersionCount.toString
    def incrementVersion() = currentVersionCount += 1
    val leaseProbe = TestProbe()
    val updateProbe = TestProbe()
    val mockKubernetesApi = new MockKubernetesApi(system, leaseProbe.ref, updateProbe.ref)
    val granted = new AtomicBoolean(false)
    val underTest = system.actorOf(LeaseActor.props(mockKubernetesApi, leaseSettings, granted))
    val senderProbe = TestProbe()
    implicit val sender: ActorRef = senderProbe.ref

    def expectHeartBeat(): Unit = {
      updateProbe.expectMsg((ownerName, currentVersion))
      incrementVersion()
      updateProbe.reply(Right(LeaseResource(Some(ownerName), currentVersion, System.currentTimeMillis())))
    }

    def failToGetTakenLease(leaseOwner: String): Unit = {
      underTest.tell(LeaseActor.Acquire(), senderProbe.ref)
      leaseProbe.expectMsg(leaseName)
      leaseProbe.reply(LeaseResource(Some(leaseOwner), currentVersion, System.currentTimeMillis()))
      senderProbe.expectMsg(LeaseTaken)
    }

    def acquireLease(callback: Option[Throwable] => Unit = ConstantFun.scalaAnyToUnit): Unit = {
      underTest.tell(LeaseActor.Acquire(callback), senderProbe.ref)
      leaseProbe.expectMsg(leaseName)
      leaseProbe.reply(LeaseResource(None, currentVersion, 1L))
      updateProbe.expectMsg((ownerName, currentVersion))
      incrementVersion()
      updateProbe.reply(Right(LeaseResource(Some(ownerName), currentVersion, System.currentTimeMillis())))
      senderProbe.expectMsg(LeaseAcquired)
    }

    def acquireLeaseWithoutRead(clientName: String): Unit = {
      underTest ! LeaseActor.Acquire()
      updateProbe.expectMsg((clientName, currentVersion))
      incrementVersion()
      updateProbe.reply(Right(LeaseResource(Some(clientName), currentVersion, System.currentTimeMillis())))
      senderProbe.expectMsg(LeaseAcquired)
    }

    def releaseLease(): Unit = {
      underTest ! Release()
      updateProbe.expectMsg(("", currentVersion))
      incrementVersion()
      updateProbe.reply(Right(LeaseResource(None, currentVersion, System.currentTimeMillis())))
      senderProbe.expectMsg(LeaseReleased)
    }

    def goToGrantingFromIdle(clientName: String): Unit = {
      underTest ! Acquire()
      // get the current state
      incrementVersion()
      leaseProbe.expectMsg(leaseName)
      leaseProbe.reply(LeaseResource(None, currentVersion, System.currentTimeMillis()))
      updateProbe.expectMsg((clientName, currentVersion))
    }

    def failToGetLeaseDuringGrantingUpdate(): Unit = {
      underTest ! LeaseActor.Acquire()
      leaseProbe.expectMsg(leaseName)
      leaseProbe.reply(LeaseResource(None, currentVersion, System.currentTimeMillis()))
      updateProbe.expectMsg((ownerName, currentVersion))
      incrementVersion()
      updateProbe.reply(Left(LeaseResource(Some("some one else :("), currentVersion, System.currentTimeMillis())))
      senderProbe.expectMsg(LeaseTaken)
    }

    def k8sApiFailureDuringRead(): Unit = {
      val k8sApiFailure = new LeaseException("Failed to communicate with API server")
      underTest ! LeaseActor.Acquire()
      leaseProbe.expectMsg(leaseName)
      leaseProbe.reply(Failure(k8sApiFailure))
      senderProbe.expectMsg(Failure(k8sApiFailure))
    }

    def heartBeatConflict(): Unit = {
      updateProbe.expectMsg((ownerName, currentVersion))
      incrementVersion()
      updateProbe.reply(Left(LeaseResource(Some("i stole your lock"), currentVersion, System.currentTimeMillis())))
      awaitAssert {
        granted.get() shouldEqual false
      }
    }

    def heartBeatFailure(): Unit = {
      updateProbe.expectMsg((ownerName, currentVersion))
      incrementVersion()
      updateProbe.reply(Failure(new LeaseException("Failed to communicate with API server")))
      awaitAssert {
        granted.get() shouldEqual false
      }
    }

  }
}
