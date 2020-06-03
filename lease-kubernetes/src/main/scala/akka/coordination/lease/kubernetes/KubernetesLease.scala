/*
 * Copyright (C) 2017-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.coordination.lease.kubernetes

import java.util.concurrent.atomic.{ AtomicBoolean, AtomicInteger }

import akka.actor.ExtendedActorSystem
import akka.coordination.lease.{ LeaseException, LeaseSettings, LeaseTimeoutException }
import akka.coordination.lease.scaladsl.Lease
import akka.coordination.lease.kubernetes.LeaseActor._
import akka.coordination.lease.kubernetes.internal.KubernetesApiImpl
import akka.dispatch.ExecutionContexts
import akka.pattern.AskTimeoutException
import akka.util.{ ConstantFun, Timeout }

import scala.concurrent.Future

object KubernetesLease {
  val configPath = "akka.coordination.lease.kubernetes"
  private val leaseCounter = new AtomicInteger(1)
}

class KubernetesLease private[akka] (system: ExtendedActorSystem, leaseTaken: AtomicBoolean, settings: LeaseSettings)
    extends Lease(settings) {

  private val k8sSettings = KubernetesSettings(settings.leaseConfig, settings.timeoutSettings)
  private val k8sApi = new KubernetesApiImpl(system, k8sSettings)
  private val leaseActor = system.systemActorOf(
    LeaseActor.props(k8sApi, settings, leaseTaken),
    s"kubernetesLease${KubernetesLease.leaseCounter.incrementAndGet}-${settings.leaseName}-${settings.ownerName}"
  )

  def this(leaseSettings: LeaseSettings, system: ExtendedActorSystem) =
    this(system, new AtomicBoolean(false), leaseSettings)

  import akka.pattern.ask
  import system.dispatcher

  private implicit val timeout: Timeout = Timeout(settings.timeoutSettings.operationTimeout)

  override def checkLease(): Boolean = leaseTaken.get()

  override def release(): Future[Boolean] = {
    // replace with transform once 2.11 dropped
    (leaseActor ? Release())
      .flatMap {
        case LeaseReleased       => Future.successful(true)
        case InvalidRequest(msg) => Future.failed(new LeaseException(msg))
      }(ExecutionContexts.sameThreadExecutionContext)
      .recoverWith {
        case _: AskTimeoutException =>
          Future.failed(new LeaseTimeoutException(
            s"Timed out trying to release lease [${settings.leaseName}, ${settings.ownerName}]. It may still be taken."))
      }
  }

  override def acquire(): Future[Boolean] = {
    acquire(ConstantFun.scalaAnyToUnit)

  }
  override def acquire(leaseLostCallback: Option[Throwable] => Unit): Future[Boolean] = {
    // replace with transform once 2.11 dropped
    (leaseActor ? Acquire(leaseLostCallback))
      .flatMap {
        case LeaseAcquired       => Future.successful(true)
        case LeaseTaken          => Future.successful(false)
        case InvalidRequest(msg) => Future.failed(new LeaseException(msg))
      }
      .recoverWith {
        case _: AskTimeoutException =>
          Future.failed[Boolean](new LeaseTimeoutException(
            s"Timed out trying to acquire lease [${settings.leaseName}, ${settings.ownerName}]. It may still be taken."))
      }(ExecutionContexts.sameThreadExecutionContext)
  }
}
