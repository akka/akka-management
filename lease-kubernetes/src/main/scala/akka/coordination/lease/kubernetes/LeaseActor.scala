/*
 * Copyright (C) 2017-2025 Lightbend Inc. <https://akka.io>
 */

package akka.coordination.lease.kubernetes

import java.util.concurrent.atomic.AtomicBoolean

import akka.actor.Status.Failure
import akka.actor.{ ActorRef, DeadLetterSuppression, FSM, LoggingFSM, Props }
import akka.annotation.InternalApi
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
  case class PendingReadData(
      replyTo: ActorRef,
      leaseLostCallback: Option[Throwable] => Unit,
      acquireStartTime: Long = System.nanoTime(),
      retryCount: Int = 0)
      extends Data
      with ReplyRequired
  case class OperationInProgress(
      replyTo: ActorRef,
      version: String,
      leaseLostCallback: Option[Throwable] => Unit,
      operationStartTime: Long = System.nanoTime(),
      acquireStartTime: Long = System.nanoTime(),
      retryCount: Int = 0)
      extends Data
      with ReplyRequired
  case class GrantedVersion(
      version: String,
      leaseLostCallback: Option[Throwable] => Unit,
      lastHeartbeatTime: Long = System.nanoTime(),
      retryCount: Int = 0)
      extends Data

  sealed trait Command
  case class Acquire(leaseLostCallback: Option[Throwable] => Unit = ConstantFun.scalaAnyToUnit) extends Command
  case class Release() extends Command

  // internal
  private case class ReadResponse(response: LeaseResource) extends Command
  private case class WriteResponse(response: Either[LeaseResource, LeaseResource]) extends Command
  private case object Heartbeat extends Command
  private case class HeartbeatRetry(retryCount: Int) extends Command
  private case class AcquireRetry(retryCount: Int) extends Command

  sealed trait Response
  case object LeaseAcquired extends Response
  case object LeaseTaken extends Response
  case object LeaseReleased extends Response with DeadLetterSuppression
  case class InvalidRequest(reason: String) extends Response with DeadLetterSuppression

  def props(k8sApi: KubernetesApi, settings: LeaseSettings, leaseName: String, granted: AtomicBoolean): Props = {
    Props(new LeaseActor(k8sApi, settings, leaseName, granted))
  }

}

/**
 * INTERNAL API
 */
@InternalApi
private[akka] class LeaseActor(
    k8sApi: KubernetesApi,
    settings: LeaseSettings,
    leaseName: String,
    granted: AtomicBoolean)
    extends LoggingFSM[LeaseActor.State, LeaseActor.Data] {

  import akka.coordination.lease.kubernetes.LeaseActor._
  import akka.pattern.pipe
  import context.dispatcher

  private val ownerName = settings.ownerName

  startWith(Idle, ReadRequired)

  when(Idle) {
    case Event(Acquire(leaseLostCallback), ReadRequired) =>
      // Send off read, pipe result back to self
      pipe(k8sApi.readOrCreateLeaseResource(leaseName).map(ReadResponse.apply)).to(self)
      goto(PendingRead).using(PendingReadData(sender(), leaseLostCallback))

    // Initial read can be skipped as we have a version
    case Event(Acquire(leaseLostCallback), LeaseCleared(version)) =>
      pipe(k8sApi.updateLeaseResource(leaseName, ownerName, version).map(r => WriteResponse(r))).to(self)
      goto(Granting).using(OperationInProgress(sender(), version, leaseLostCallback))
  }

  when(PendingRead) {
    // Lock not taken
    case Event(ReadResponse(LeaseResource(None, version, _)), prd: PendingReadData) =>
      tryGetLease(version, prd.replyTo, prd.leaseLostCallback, prd.acquireStartTime)
    case Event(ReadResponse(LeaseResource(Some(currentOwner), version, time)), prd: PendingReadData)
        if currentOwner == ownerName =>
      // We have the lock from a different incarnation
      if (hasLeaseTimedOut(time)) {
        log.warning(
          "Lease {} requested by client {} is already owned by client. Previous lease was not released due to ungraceful shutdown. " +
          "Lease time {} is close or past expiry so re-acquiring",
          leaseName,
          ownerName,
          time
        )
        tryGetLease(version, prd.replyTo, prd.leaseLostCallback, prd.acquireStartTime)
      } else {
        log.warning(
          "Lease {} requested by client {} is already owned by client. Previous lease was not released due to ungraceful shutdown. " +
          "Lease is still within timeout so granting immediately",
          leaseName,
          ownerName
        )
        prd.replyTo ! LeaseAcquired
        goto(Granted).using(GrantedVersion(version, prd.leaseLostCallback))
      }
    case Event(ReadResponse(LeaseResource(Some(currentOwner), version, time)), prd: PendingReadData) =>
      if (hasLeaseTimedOut(time)) {
        log.warning(
          "Lease {} has reached TTL. Owner {} has failed to heartbeat, have they crashed?. Allowing {} to try and take lease",
          leaseName,
          currentOwner,
          ownerName)
        tryGetLease(version, prd.replyTo, prd.leaseLostCallback, prd.acquireStartTime)
      } else {
        prd.replyTo ! LeaseTaken
        // Even though we have a version there is no benefit to storing it as we can't update a lease that has a client
        goto(Idle).using(ReadRequired)
      }
    case Event(Failure(t), prd: PendingReadData) =>
      val nextRetry = prd.retryCount + 1
      val delay = acquireRetryDelay(nextRetry)
      if (canRetryWithin(prd.acquireStartTime, delay)) {
        val elapsed = (System.nanoTime() - prd.acquireStartTime).nanos
        log.warning(
          "Failure during lease read: [{}]. Time since acquire started [{}]. Scheduling retry [{}] in [{}].",
          t.getMessage,
          elapsed.pretty,
          nextRetry,
          delay.pretty
        )
        startSingleTimer("acquire-retry", AcquireRetry(nextRetry), delay)
        stay().using(prd.copy(retryCount = nextRetry))
      } else {
        val elapsed = (System.nanoTime() - prd.acquireStartTime).nanos
        log.warning(
          "Failure during lease read: [{}]. Time since acquire started [{}] exceeds retry budget after [{}] attempts.",
          t.getMessage,
          elapsed.pretty,
          prd.retryCount + 1
        )
        prd.replyTo ! Failure(t)
        goto(Idle).using(ReadRequired)
      }
    case Event(AcquireRetry(retryCount), _: PendingReadData) =>
      log.info("Retrying lease read, attempt [{}]", retryCount)
      pipe(k8sApi.readOrCreateLeaseResource(leaseName).map(ReadResponse.apply)).to(self)
      stay()
  }

  when(Granting) {
    case Event(WriteResponse(Right(response)), op: OperationInProgress) =>
      require(
        op.version != response.version,
        s"Update response from Kubernetes API should not return the same version: Response: $response. Client: $op")
      val operationDuration = System.nanoTime() - op.operationStartTime
      if (operationDuration > (settings.timeoutSettings.heartbeatTimeout.toNanos / 2)) {
        log.warning("API server took too long to respond to update: {}. ", operationDuration.nanos.pretty)
        op.replyTo ! Failure(
          new LeaseTimeoutException(s"API server took too long to respond: ${operationDuration.nanos.pretty}"))
        goto(Idle).using(ReadRequired)
      } else {
        granted.set(true)
        op.replyTo ! LeaseAcquired
        goto(Granted).using(GrantedVersion(response.version, op.leaseLostCallback))
      }

    case Event(WriteResponse(Left(LeaseResource(None, version, _))), op: OperationInProgress) =>
      require(op.version != version)
      // Try again as lock version has moved on but is not taken; the follow-up update's
      // response is the authoritative reply to the caller.
      pipe(k8sApi.updateLeaseResource(leaseName, ownerName, version).map(r => WriteResponse(r))).to(self)
      stay().using(op.copy(version = version))
    case Event(WriteResponse(Left(LeaseResource(Some(currentOwner), version, _))), op: OperationInProgress)
        if currentOwner == ownerName =>
      // A previous attempt's write actually succeeded on the server even though we observed a transient
      // failure (or never received the response). Treat as acquired.
      granted.set(true)
      op.replyTo ! LeaseAcquired
      goto(Granted).using(GrantedVersion(version, op.leaseLostCallback))
    case Event(WriteResponse(Left(LeaseResource(Some(_), _, _))), op: OperationInProgress) =>
      // The audacity, someone else has taken the lease :(
      op.replyTo ! LeaseTaken
      goto(Idle).using(ReadRequired) // can't use version as another owner has the lock
    case Event(Failure(t), op: OperationInProgress) =>
      val nextRetry = op.retryCount + 1
      val delay = acquireRetryDelay(nextRetry)
      if (canRetryWithin(op.acquireStartTime, delay)) {
        val elapsed = (System.nanoTime() - op.acquireStartTime).nanos
        log.warning(
          "Failure during lease update: [{}]. Time since acquire started [{}]. Scheduling retry [{}] in [{}].",
          t.getMessage,
          elapsed.pretty,
          nextRetry,
          delay.pretty
        )
        startSingleTimer("acquire-retry", AcquireRetry(nextRetry), delay)
        stay().using(op.copy(retryCount = nextRetry))
      } else {
        val elapsed = (System.nanoTime() - op.acquireStartTime).nanos
        log.warning(
          "Failure during lease update: [{}]. Time since acquire started [{}] exceeds retry budget after [{}] attempts.",
          t.getMessage,
          elapsed.pretty,
          op.retryCount + 1
        )
        op.replyTo ! Failure(t)
        goto(Idle).using(ReadRequired)
      }
    case Event(AcquireRetry(retryCount), op: OperationInProgress) =>
      log.info("Retrying lease update, attempt [{}]. Version [{}]", retryCount, op.version)
      pipe(k8sApi.updateLeaseResource(leaseName, ownerName, op.version).map(WriteResponse.apply)).to(self)
      // reset operationStartTime so the "too slow" check in Granting measures only the retried call
      stay().using(op.copy(operationStartTime = System.nanoTime()))
  }

  when(Granted) {
    case Event(Heartbeat, GrantedVersion(version, _, _, _)) =>
      log.debug("Heartbeat: updating lease time. Version {}", version)
      pipe(k8sApi.updateLeaseResource(leaseName, ownerName, version).map(WriteResponse.apply)).to(self)
      stay()
    case Event(WriteResponse(Right(resource)), gv: GrantedVersion) =>
      require(
        resource.owner.contains(ownerName),
        "response from API server has different owner for success: " + resource)
      log.debug("Heartbeat: lease time updated: Version {}", resource.version)
      startSingleTimer("heartbeat", Heartbeat, settings.timeoutSettings.heartbeatInterval)
      stay().using(gv.copy(version = resource.version, lastHeartbeatTime = System.nanoTime(), retryCount = 0))
    case Event(WriteResponse(Left(lr @ _)), GrantedVersion(_, leaseLost, _, _)) =>
      log.warning("Conflict during heartbeat to lease {}. Lease assumed to be released.", lr)
      granted.set(false)
      executeLeaseLockCallback(leaseLost, None)
      goto(Idle).using(ReadRequired)
    case Event(Failure(t), gv @ GrantedVersion(_, leaseLost, lastHeartbeatTime, retryCount)) =>
      if (hasTimeLeftForHeartbeatRetry(lastHeartbeatTime)) {
        val elapsed = (System.nanoTime() - lastHeartbeatTime).nanos
        val nextRetry = retryCount + 1
        val delay = heartbeatRetryDelay(nextRetry)
        log.warning(
          "Failure during heartbeat to lease: [{}]. Time since last successful heartbeat [{}]. Scheduling retry [{}] in [{}].",
          t.getMessage,
          elapsed.pretty,
          nextRetry,
          delay.pretty
        )
        startSingleTimer("heartbeat-retry", HeartbeatRetry(nextRetry), delay)
        stay().using(gv.copy(retryCount = nextRetry))
      } else {
        val elapsed = (System.nanoTime() - lastHeartbeatTime).nanos
        log.warning(
          "Failure during heartbeat to lease: [{}]. Time since last successful heartbeat [{}] exceeds safe retry window after [{}] retries. Lease assumed lost.",
          t.getMessage,
          elapsed.pretty,
          retryCount
        )
        granted.set(false)
        executeLeaseLockCallback(leaseLost, Some(t))
        goto(Idle).using(ReadRequired)
      }
    case Event(HeartbeatRetry(retryCount), GrantedVersion(version, leaseLost, lastHeartbeatTime, _)) =>
      if (hasTimeLeftForHeartbeatRetry(lastHeartbeatTime)) {
        log.info("Retrying heartbeat, attempt [{}]. Version [{}]", retryCount, version)
        pipe(k8sApi.updateLeaseResource(leaseName, ownerName, version).map(WriteResponse.apply)).to(self)
        stay()
      } else {
        val elapsed = (System.nanoTime() - lastHeartbeatTime).nanos
        log.warning(
          "Heartbeat retry [{}] arrived too late, time since last successful heartbeat [{}] exceeds safe retry window. Lease assumed lost.",
          retryCount,
          elapsed.pretty
        )
        granted.set(false)
        executeLeaseLockCallback(leaseLost, None)
        goto(Idle).using(ReadRequired)
      }
    case Event(Release(), GrantedVersion(version, leaseLost, _, _)) =>
      pipe(k8sApi.updateLeaseResource(leaseName, "", version).map(WriteResponse.apply)).to(self)
      goto(Releasing).using(OperationInProgress(sender(), version, leaseLost))
    case Event(Acquire(leaseLostCallback), gv: GrantedVersion) =>
      sender() ! LeaseAcquired
      stay().using(gv.copy(leaseLostCallback = leaseLostCallback))
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
    case Event(WriteResponse(Right(lr)), op: OperationInProgress) =>
      require(lr.owner.isEmpty, "Released lease has unexpected owner: " + lr)
      op.replyTo ! LeaseReleased
      goto(Idle).using(LeaseCleared(lr.version))
    case Event(WriteResponse(Left(lr @ LeaseResource(None, _, _))), op: OperationInProgress) =>
      log.warning(
        "Release conflict and owner has been removed: {}. Lease will continue to work but TTL must have been reached to allow another node to remove lease.",
        lr
      )
      op.replyTo ! LeaseReleased
      goto(Idle).using(ReadRequired)
    case Event(WriteResponse(Left(lr @ LeaseResource(Some(_), _, _))), op: OperationInProgress) =>
      log.warning(
        "Release conflict and owner has changed: {}. Lease will continue to work but TTL must have been reached to allow another node to update the lease.",
        lr
      )
      op.replyTo ! LeaseReleased
      goto(Idle).using(ReadRequired)
  }

  whenUnhandled {
    case Event(Acquire(_), data @ _) =>
      log.info(
        "Acquire request for owner {} lease {} while previous acquire/release still in progress. Current state: {}",
        ownerName,
        leaseName,
        stateName)
      stay().using(data)
    case Event(Release(), data @ _) =>
      log.info(
        "Release request for owner {} lease {} while previous acquire/release still in progress. Current state: {}",
        ownerName,
        leaseName,
        stateName)
      sender() ! InvalidRequest("Tried to release a lease that is not acquired")
      stay().using(data)
    case Event(_: HeartbeatRetry, _) =>
      // stale retry after leaving Granted state, ignore
      stay()
    case Event(_: AcquireRetry, _) =>
      // stale retry after leaving PendingRead / Granting states, ignore
      stay()
    case Event(Failure(t), replyRequired: ReplyRequired) =>
      log.warning(
        "Failure communicating with the API server for owner {} lease {}: [{}]. Current state: {}",
        ownerName,
        leaseName,
        t.getMessage,
        stateName)
      replyRequired.replyTo ! Failure(t)
      goto(Idle).using(ReadRequired)
  }

  onTransition {
    case _ -> Granted =>
      cancelTimer("acquire-retry")
      startSingleTimer("heartbeat", Heartbeat, settings.timeoutSettings.heartbeatInterval)
    case Granted -> _ =>
      cancelTimer("heartbeat")
      cancelTimer("heartbeat-retry")
      granted.set(false)
    case _ -> Idle =>
      cancelTimer("acquire-retry")
  }

  private def tryGetLease(
      version: String,
      reply: ActorRef,
      leaseLost: Option[Throwable] => Unit,
      acquireStartTime: Long): FSM.State[LeaseActor.State, Data] = {
    pipe(k8sApi.updateLeaseResource(leaseName, ownerName, version).map(r => WriteResponse(r))).to(self)
    goto(Granting).using(OperationInProgress(reply, version, leaseLost, acquireStartTime = acquireStartTime))
  }

  // Exponential backoff: base/4, base/2, base, base, ... capped at base.
  // Conservative cadence so a struggling API server is not hammered with retries.
  private def exponentialRetryDelay(base: FiniteDuration, retryCount: Int): FiniteDuration = {
    val delay = base / 4 * (1L << math.min(retryCount - 1, 2))
    delay.min(base)
  }

  private def acquireRetryDelay(retryCount: Int): FiniteDuration =
    exponentialRetryDelay(settings.timeoutSettings.operationTimeout, retryCount)

  private def heartbeatRetryDelay(retryCount: Int): FiniteDuration =
    exponentialRetryDelay(settings.timeoutSettings.heartbeatInterval, retryCount)

  private def canRetryWithin(acquireStartTime: Long, delay: FiniteDuration): Boolean = {
    // Caller's ask times out at operationTimeout. Only schedule a retry if after the delay
    // we'll still have time for another API call within half of the caller's budget.
    val elapsed = System.nanoTime() - acquireStartTime
    elapsed + delay.toNanos < settings.timeoutSettings.operationTimeout.toNanos / 2
  }

  private def hasTimeLeftForHeartbeatRetry(lastHeartbeatTime: Long): Boolean = {
    val elapsed = (System.nanoTime() - lastHeartbeatTime).nanos
    // Other nodes consider the lease expired at: heartbeatTimeout - 2*heartbeatInterval.
    // We must give up before that, subtracting:
    // - operationTimeout: time needed to complete the retry API call itself
    // - heartbeatInterval: additional buffer for clock skew between nodes
    val otherNodeTakeover = settings.timeoutSettings.heartbeatTimeout - (2 * settings.timeoutSettings.heartbeatInterval)
    val safetyMargin = settings.timeoutSettings.operationTimeout + settings.timeoutSettings.heartbeatInterval
    elapsed < otherNodeTakeover - safetyMargin
  }

  private def hasLeaseTimedOut(leaseTime: Long): Boolean = {
    System
      .currentTimeMillis() > (leaseTime + settings.timeoutSettings.heartbeatTimeout.toMillis - (2 * settings.timeoutSettings.heartbeatInterval.toMillis))
  }
}
