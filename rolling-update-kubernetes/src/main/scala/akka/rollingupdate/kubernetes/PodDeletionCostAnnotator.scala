/*
 * Copyright (C) 2017-2025 Lightbend Inc. <https://akka.io>
 */

package akka.rollingupdate.kubernetes

import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.TimeUnit

import scala.collection.immutable
import scala.collection.immutable.SortedSet
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.DurationLong
import scala.concurrent.duration.FiniteDuration
import scala.util.control.NonFatal

import akka.Done
import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorSystem
import akka.actor.Props
import akka.actor.Status
import akka.actor.Timers
import akka.annotation.InternalApi
import akka.cluster.Cluster
import akka.cluster.ClusterEvent
import akka.cluster.Member
import akka.cluster.UniqueAddress
import akka.event.Logging.InfoLevel
import akka.event.Logging.WarningLevel
import akka.pattern.pipe
import akka.rollingupdate.OlderCostsMore
import com.typesafe.config.Config

/**
 * INTERNAL API
 *
 * Actor responsible to annotate the hosting pod with the pod-deletion-cost or
 * update the PodCost CR depending on configuration.
 * It will automatically retry upon a fixed-configurable delay if the annotation fails.
 */
@InternalApi private[kubernetes] final class PodDeletionCostAnnotator(
    settings: KubernetesSettings,
    costSettings: PodDeletionCostSettings,
    kubernetesApi: KubernetesApi,
    crName: Option[String])
    extends Actor
    with ActorLogging
    with Timers {
  import PodDeletionCostAnnotator._

  private val podName = settings.podName
  private val resourceLogDescription = if (crName.isDefined) "PodCost CR" else "pod-deletion-cost annotation"

  private val cluster = Cluster(context.system)

  Cluster(context.system).subscribe(context.self, classOf[ClusterEvent.MemberUp], classOf[ClusterEvent.MemberRemoved])

  def receive: Receive = idle(0, SortedSet.empty(Member.ageOrdering), 0)

  private def idle(deletionCost: Int, membersByAgeDesc: SortedSet[Member], retryNr: Int): Receive = {
    case cs @ ClusterEvent.CurrentClusterState(members, _, _, _, _) =>
      log.debug("Received CurrentClusterState {}", cs)
      updateIfNewCost(deletionCost, membersByAgeDesc ++ members, retryNr) // ordering used is from the first operand (so, by age)

    case ClusterEvent.MemberUp(m) =>
      log.debug("Received MemberUp {}", m)
      updateIfNewCost(deletionCost, membersByAgeDesc + m, retryNr)

    case ClusterEvent.MemberRemoved(m, _) =>
      log.debug("Received MemberRemoved {}", m)
      updateIfNewCost(deletionCost, membersByAgeDesc - m, retryNr)

    case PodAnnotated =>
      log.debug("{} updated successfully to [{}]", resourceLogDescription, deletionCost)
      // cancelling an eventual retry in case the operation succeeded in the meantime
      timers.cancel(RetryTimerId)
      context.become(idle(deletionCost, membersByAgeDesc, 0))

    case ScheduleRetry(ex) =>
      val ll = if (retryNr < 3) InfoLevel else WarningLevel
      log.log(
        ll,
        s"Failed to update $resourceLogDescription: [$ex]. Scheduled retry with fixed delay of ${costSettings.retryDelay}, retry number $retryNr.")

      val retryDelay =
        if (crName.isDefined)
          // add some random delay to minimize risk of conflicts
          costSettings.retryDelay + (costSettings.retryDelay * ThreadLocalRandom.current().nextDouble(0.1))
            .asInstanceOf[FiniteDuration]
        else
          costSettings.retryDelay
      timers.startSingleTimer(RetryTimerId, RetryAnnotate, retryDelay)
      context.become(underRetryBackoff(membersByAgeDesc, retryNr))

    case GiveUp(er: String) =>
      log.error(
        "There was a client error when trying to set {}. " +
        "Not retrying, check configuration. Error: {}",
        resourceLogDescription,
        er)

    case Status.Failure(exc) =>
      throw new IllegalStateException(
        "Unexpected failure, Future failure should have been recovered " +
        "to message before pipeTo self. This is a bug.",
        exc)

    case msg => log.debug("Ignoring message {}", msg)
  }

  private def underRetryBackoff(membersByAgeDesc: SortedSet[Member], retryNr: Int): Receive = {
    case ClusterEvent.MemberUp(m) =>
      log.debug("Received while on retry backoff MemberUp {}", m)
      context.become(underRetryBackoff(membersByAgeDesc + m, retryNr))

    case ClusterEvent.MemberRemoved(m, _) =>
      log.debug("Received while on retry backoff MemberRemoved {}", m)
      context.become(underRetryBackoff(membersByAgeDesc - m, retryNr))

    case RetryAnnotate =>
      updateIfNewCost(Int.MinValue, membersByAgeDesc, retryNr + 1)

    case Status.Failure(exc) =>
      throw new IllegalStateException(
        "Unexpected failure, Future failure should have been recovered " +
        "to message before pipeTo self. This is a bug.",
        exc)

    case msg => log.debug("Under retry backoff, ignoring message {}", msg)
  }

  private def updateIfNewCost(existingCost: Int, membersByAgeDesc: immutable.SortedSet[Member], retryNr: Int): Unit = {

    val podsToAnnotate = membersByAgeDesc.take(costSettings.annotatedPodsNr)
    val newCost: Int = OlderCostsMore.costOf(cluster.selfMember, podsToAnnotate).getOrElse(0)
    log.debug(
      "Calculated cost={} (previously {}) for member={} in members by age (desc): {}",
      newCost,
      existingCost,
      cluster.selfMember,
      membersByAgeDesc)

    if (newCost != existingCost) {
      log.info(
        "Updating {} for pod: [{}] with cost: [{}]. Namespace: [{}]",
        resourceLogDescription,
        podName,
        newCost,
        kubernetesApi.namespace
      )

      implicit val dispatcher: ExecutionContext = context.system.dispatcher
      updatePodCost(
        kubernetesApi,
        crName,
        podName,
        newCost,
        cluster.selfUniqueAddress,
        membersByAgeDesc,
        settings.customResourceSettings.cleanupAfter)(context.system).pipeTo(self)

      context.become(idle(newCost, membersByAgeDesc, retryNr))
    } else {
      context.become(idle(existingCost, membersByAgeDesc, retryNr))
    }
  }

}

/**
 * INTERNAL API
 * @param annotatedPodsNr the number of members of the cluster that need to be annotated
 * @param retryDelay fixed time delay before next attempt to annotate in case the previous one failed
 */
@InternalApi private[kubernetes] final case class PodDeletionCostSettings(
    annotatedPodsNr: Int,
    retryDelay: FiniteDuration)

/**
 * INTERNAL API
 */
@InternalApi private[kubernetes] object PodDeletionCostSettings {
  val configPath: String = "pod-deletion-cost"
  def apply(config: Config): PodDeletionCostSettings =
    PodDeletionCostSettings(
      config.getInt(s"$configPath.annotated-pods-nr"),
      config.getDuration(s"$configPath.retry-delay", TimeUnit.SECONDS).seconds
    )
}

/**
 * INTERNAL API
 */
@InternalApi private[kubernetes] object PodDeletionCostAnnotator {
  case object RetryTimerId
  case object RetryAnnotate
  sealed trait RequestResult
  case object PodAnnotated extends RequestResult
  case class ScheduleRetry(cause: String) extends RequestResult
  case class GiveUp(cause: String) extends RequestResult

  def props(
      settings: KubernetesSettings,
      costSettings: PodDeletionCostSettings,
      kubernetesApi: KubernetesApi,
      crName: Option[String]
  ): Props =
    Props(new PodDeletionCostAnnotator(settings, costSettings, kubernetesApi, crName))

  private def updatePodCost(
      kubernetesApi: KubernetesApi,
      crNameOpt: Option[String],
      podName: String,
      newCost: Int,
      selfUniqueAddress: UniqueAddress,
      membersByAgeDesc: immutable.SortedSet[Member],
      cleanupAfter: FiniteDuration)(implicit system: ActorSystem): Future[RequestResult] = {
    import system.dispatcher
    crNameOpt match {
      case Some(crName) =>
        val response =
          kubernetesApi.readOrCreatePodCostResource(crName).flatMap { cr =>
            val now = System.currentTimeMillis()
            val newPodCost =
              PodCost(podName, newCost, selfUniqueAddress.address.toString, selfUniqueAddress.longUid, now)
            val newPods = cr.pods.filterNot { podCost =>
                // remove entry that is to be added for this podName
                podCost.podName == podName ||
                // remove entries that don't exist in the cluster membership any more
                (podCost.uniqueAddress.address.system == selfUniqueAddress.address.system && // only same cluster
                now - podCost.time > cleanupAfter.toMillis && // in case new member hasn't been seen yet
                !membersByAgeDesc.exists(_.uniqueAddress == podCost.uniqueAddress) // removed, not in cluster membership
                )
              } :+ newPodCost
            kubernetesApi.updatePodCostResource(crName, cr.version, newPods)
          }
        updatePodCostResourceResult(response)
      case None =>
        val response = kubernetesApi.updatePodDeletionCostAnnotation(podName, newCost)
        updatePodDeletionCostAnnotationResult(response)
    }
  }

  private def updatePodDeletionCostAnnotationResult(futResponse: Future[Done])(
      implicit system: ActorSystem): Future[RequestResult] = {
    import system.dispatcher
    futResponse
      .map {
        case Done => PodAnnotated
      }
      .recover {
        case e: PodCostClientException => GiveUp(e.getMessage)
        case NonFatal(e)               => ScheduleRetry(e.getMessage)
      }
  }

  private def updatePodCostResourceResult(futResponse: Future[Either[PodCostResource, PodCostResource]])(
      implicit system: ActorSystem): Future[RequestResult] = {
    import system.dispatcher
    futResponse
      .map {
        case Right(_) => PodAnnotated
        case Left(_)  => ScheduleRetry("Request failed with conflict")
      }
      .recover {
        case e: PodCostClientException => GiveUp(e.getMessage)
        case NonFatal(e)               => ScheduleRetry(e.getMessage)
      }

  }
}
