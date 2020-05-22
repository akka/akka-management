/*
 * Copyright (C) 2017-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.coordination.lease.kubernetes

import java.util.concurrent.atomic.AtomicBoolean

import akka.actor.Status.Failure
import akka.actor.{ ActorRef, DeadLetterSuppression, FSM, LoggingFSM, Props }
import akka.annotation.InternalApi
import akka.coordination.lease.kubernetes.LeaseActor._
import akka.coordination.lease.{ LeaseSettings, LeaseTimeoutException }
import akka.util.ConstantFun
import akka.util.PrettyDuration._

import scala.concurrent.duration._
import scala.util.control.NonFatal

/**
 * INTERNAL API
 */
@InternalApi
private[akka] object LeaseActor {

  sealed trait State
  case object Idle extends State
  case object PendingRead extends State
  case object Granting extends State
  case object Granted extends State
  case object Releasing extends State

  sealed trait Data
  case object ReadRequired extends Data

  // Known version from when the lease was cleared. A subsequent update can try without reading
  // with the given version as it was from an update that set client to None
  case class LeaseCleared(version: String) extends Data

  sealed trait ReplyRequired {
    def replyTo: ActorRef
  }
  // Awaiting a read to try and get the lease
  case class PendingReadData(replyTo: ActorRef, leaseLostCallback: Option[Throwable] => Unit) extends Data with ReplyRequired
  case class OperationInProgress(replyTo: ActorRef, version: String, leaseLostCallback: Option[Throwable] => Unit, operationStartTime: Long = System.nanoTime()) extends Data with ReplyRequired
  case class GrantedVersion(version: String, leaseLostCallback: Option[Throwable] => Unit) extends Data

  sealed trait Command
  case class Acquire(leaseLostCallback: Option[Throwable] => Unit = ConstantFun.scalaAnyToUnit) extends Command
  case class Release() extends Command

  // internal
  private case class ReadResponse(response: LeaseResource) extends Command
  private case class WriteResponse(response: Either[LeaseResource, LeaseResource]) extends Command
  private case object Heartbeat extends Command

  sealed trait Response
  case object LeaseAcquired extends Response
  case object LeaseTaken extends Response
  case object LeaseReleased extends Response with DeadLetterSuppression
  case class InvalidRequest(reason: String) extends Response with DeadLetterSuppression

  def props(k8sApi: KubernetesApi, settings: LeaseSettings, granted: AtomicBoolean): Props = {
    Props(new LeaseActor(k8sApi, settings, granted))
  }

}

/**
 * INTERNAL API
 */
@InternalApi
private[akka] class LeaseActor(k8sApi: KubernetesApi, settings: LeaseSettings, granted: AtomicBoolean) extends LoggingFSM[State, Data] {

  import akka.pattern.pipe
  import settings._
  import context.dispatcher

  startWith(Idle, ReadRequired)

  when(Idle) {
    case Event(Acquire(leaseLostCallback), ReadRequired) =>
      // Send off read, pipe result back to self
      pipe(k8sApi.readOrCreateLeaseResource(leaseName).map(ReadResponse)).to(self)
      goto(PendingRead) using PendingReadData(sender(), leaseLostCallback)

    // Initial read can be skipped as we have a version
    case Event(Acquire(leaseLostCallback), LeaseCleared(version)) =>
      pipe(k8sApi.updateLeaseResource(leaseName, ownerName, version).map(r => WriteResponse(r))).to(self)
      goto(Granting) using OperationInProgress(sender(), version, leaseLostCallback)
  }

  when(PendingRead) {
    // Lock not taken
    case Event(ReadResponse(LeaseResource(None, version, _)), PendingReadData(who, leaseLost)) =>
      tryGetLease(version, who, leaseLost)
    case Event(ReadResponse(LeaseResource(Some(currentOwner), version, time)), PendingReadData(who, leaseLost)) if currentOwner == ownerName =>
      // We have the lock from a different incarnation
      if (hasLeaseTimedOut(time)) {
        log.warning("Lease {} requested by client {} is already owned by client. Previous lease was not released due to ungraceful shutdown. " +
          "Lease time {} is close or past expiry so re-acquiring", leaseName, ownerName, time)
        tryGetLease(version, who, leaseLost)
      } else {
        log.warning("Lease {} requested by client {} is already owned by client. Previous lease was not released due to ungraceful shutdown. " +
          "Lease is still within timeout so granting immediately", leaseName, ownerName)
        who ! LeaseAcquired
        goto(Granted) using GrantedVersion(version, leaseLost)
      }
    case Event(ReadResponse(LeaseResource(Some(currentOwner), version, time)), PendingReadData(who, leaseLost)) =>
      if (hasLeaseTimedOut(time)) {
        log.warning("Lease {} has reached TTL. Owner {} has failed to heartbeat, have they crashed?. Allowing {} to try and take lease", leaseName, currentOwner, ownerName)
        tryGetLease(version, who, leaseLost)
      } else {
        who ! LeaseTaken
        // Even though we have a version there is no benefit to storing it as we can't update a lease that has a client
        goto(Idle) using ReadRequired
      }
  }

  when(Granting) {
    case Event(WriteResponse(Right(response)), cc @ OperationInProgress(who, oldVersion, leaseLost, operationStartTime)) =>
      require(oldVersion != response.version, s"Update response from Kubernetes API should not return the same version: Response: $response. Client: $cc")
      val operationDuration = System.nanoTime() - operationStartTime
      if (operationDuration > (settings.timeoutSettings.heartbeatTimeout.toNanos / 2)) {
        log.warning("API server took too long to respond to update: {}. ", operationDuration.nanos.pretty)
        who ! Failure(new LeaseTimeoutException(s"API server took too long to respond: ${operationDuration.nanos.pretty}"))
        goto(Idle) using ReadRequired
      } else {
        granted.set(true)
        who ! LeaseAcquired
        goto(Granted) using GrantedVersion(response.version, leaseLost)
      }

    case Event(WriteResponse(Left(LeaseResource(None, version, _))), OperationInProgress(who, oldVersion, _, _)) =>
      require(oldVersion != version)
      who ! LeaseAcquired
      // Try again as lock version has moved on but is not taken
      pipe(k8sApi.updateLeaseResource(leaseName, ownerName, version).map(r => WriteResponse(r))).to(self)
      stay
    case Event(WriteResponse(Left(LeaseResource(Some(_), _, _))), OperationInProgress(who, _, _, _)) =>
      // The audacity, someone else has taken the lease :(
      who ! LeaseTaken
      goto(Idle) using ReadRequired // can't use version as another owner has the lock
  }

  when(Granted) {
    case Event(Heartbeat, GrantedVersion(version, _)) =>
      log.debug("Heartbeat: updating lease time. Version {}", version)
      pipe(k8sApi.updateLeaseResource(leaseName, ownerName, version).map(WriteResponse)).to(self)
      stay
    case Event(WriteResponse(Right(resource)), gv: GrantedVersion) =>
      require(resource.owner.contains(ownerName), "response from API server has different owner for success: " + resource)
      log.debug("Heartbeat: lease time updated: Version {}", resource.version)
      setTimer("heartbeat", Heartbeat, settings.timeoutSettings.heartbeatInterval, repeat = false)
      stay using gv.copy(version = resource.version)
    case Event(WriteResponse(Left(lr @ _)), GrantedVersion(_, leaseLost)) =>
      log.warning("Conflict during heartbeat to lease {}. Lease assumed to be released.", lr)
      granted.set(false)
      executeLeaseLockCallback(leaseLost, None)
      goto(Idle) using ReadRequired
    case Event(Failure(t), GrantedVersion(_, leaseLost)) =>
      // FIXME, retry if timeout far enough off: https://github.com/lightbend/akka-commercial-addons/issues/501
      log.warning("Failure during heartbeat to lease: [{}]. Lease assumed to be released.", t.getMessage)
      granted.set(false)
      executeLeaseLockCallback(leaseLost, Some(t))
      goto(Idle) using ReadRequired
    case Event(Release(), GrantedVersion(version, leaseLost)) =>
      pipe(k8sApi.updateLeaseResource(leaseName, "", version).map(WriteResponse)).to(self)
      goto(Releasing) using OperationInProgress(sender(), version, leaseLost)
    case Event(Acquire(leaseLostCallback), gv: GrantedVersion) =>
      sender() ! LeaseAcquired
      stay using gv.copy(leaseLostCallback = leaseLostCallback)
  }

  private def executeLeaseLockCallback(callback: Option[Throwable] => Unit, result: Option[Throwable]): Unit =
    try {
      callback(result)
    } catch {
      case NonFatal(t) =>
        log.warning("Lease lost callback threw exception: {}", t)
    }

  when(Releasing) {
    // FIXME deal with failure from releasing the the lock, currently handled in whenUnhandled but could retry to remove: https://github.com/lightbend/akka-commercial-addons/issues/502
    case Event(WriteResponse(Right(lr)), OperationInProgress(who, _, _, _)) =>
      require(lr.owner.isEmpty, "Released lease has unexpected owner: " + lr)
      who ! LeaseReleased
      goto(Idle) using LeaseCleared(lr.version)
    case Event(WriteResponse(Left(lr @ LeaseResource(None, _, _))), OperationInProgress(who, _, _, _)) =>
      log.warning("Release conflict and owner has been removed: {}. Lease will continue to work but TTL must have been reached to allow another node to remove lease.", lr)
      who ! LeaseReleased
      goto(Idle) using ReadRequired
    case Event(WriteResponse(Left(lr @ LeaseResource(Some(_), _, _))), OperationInProgress(who, _, _, _)) =>
      log.warning("Release conflict and owner has changed: {}. Lease will continue to work but TTL must have been reached to allow another node to update the lease.", lr)
      who ! LeaseReleased
      goto(Idle) using ReadRequired
  }

  whenUnhandled {
    case Event(Acquire(_), data @ _) =>
      log.info("Acquire request for owner {} lease {} while previous acquire/release still in progress. Current state: {}", ownerName, leaseName, stateName)
      stay using data
    case Event(Release(), data @ _) =>
      log.info("Release request for owner {} lease {} while previous acquire/release still in progress. Current state: {}", ownerName, leaseName, stateName)
      sender() ! InvalidRequest("Tried to release a lease that is not acquired")
      stay using data
    case Event(Failure(t), replyRequired: ReplyRequired) =>
      log.warning("Failure communicating with the API server for owner {} lease {}: [{}]. Current state: {}", ownerName, leaseName, t.getMessage, stateName)
      replyRequired.replyTo ! Failure(t)
      goto(Idle) using ReadRequired
  }

  onTransition {
    case _ -> Granted =>
      setTimer("heartbeat", Heartbeat, settings.timeoutSettings.heartbeatInterval, repeat = false)
    case Granted -> _ =>
      cancelTimer("heartbeat")
      granted.set(false)
  }

  private def tryGetLease(version: String, reply: ActorRef, leaseLost: Option[Throwable] => Unit): FSM.State[LeaseActor.State, Data] = {
    pipe(k8sApi.updateLeaseResource(leaseName, ownerName, version).map(r => WriteResponse(r))).to(self)
    goto(Granting) using OperationInProgress(reply, version, leaseLost)
  }

  private def hasLeaseTimedOut(leaseTime: Long): Boolean = {
    System.currentTimeMillis() > (leaseTime + settings.timeoutSettings.heartbeatTimeout.toMillis - (2 * settings.timeoutSettings.heartbeatInterval.toMillis))
  }
}
