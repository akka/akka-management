/*
 * Copyright (C) 2017-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.rollingupdate.kubernetes

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorSystem
import akka.actor.Timers
import akka.annotation.InternalApi
import akka.cluster.Cluster
import akka.cluster.ClusterEvent
import akka.cluster.Member
import akka.http.scaladsl.ConnectionContext
import akka.http.scaladsl.Http
import akka.http.scaladsl.HttpsConnectionContext
import akka.http.scaladsl.model.StatusCodes.ClientError
import akka.http.scaladsl.model._
import akka.pattern.pipe
import akka.pki.kubernetes.PemManagersProvider
import akka.rollingupdate.OlderCostsMore
import akka.rollingupdate.kubernetes.PodDeletionCostAnnotator.GiveUp
import akka.rollingupdate.kubernetes.PodDeletionCostAnnotator.PodAnnotated
import akka.rollingupdate.kubernetes.PodDeletionCostAnnotator.RetryAnnotate
import akka.rollingupdate.kubernetes.PodDeletionCostAnnotator.RetryTimerId
import akka.rollingupdate.kubernetes.PodDeletionCostAnnotator.ScheduleRetry
import akka.rollingupdate.kubernetes.PodDeletionCostAnnotator.toResult
import com.typesafe.config.Config

import java.security.KeyStore
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import javax.net.ssl.KeyManager
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import scala.collection.immutable
import scala.collection.immutable.SortedSet
import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.Future
import scala.concurrent.duration.DurationLong
import scala.concurrent.duration.FiniteDuration
import scala.util.control.NonFatal

/**
 * INTERNAL API
 *
 * Actor responsible to annotate the hosting pod with the pod-deletion-cost.
 * It will automatically retry upon a fixed-configurable delay if the annotation fails.
 */
@InternalApi private[kubernetes] final class PodDeletionCostAnnotator(
    settings: KubernetesSettings,
    apiToken: String,
    podNamespace: String,
    costSettings: PodDeletionCostSettings)
    extends Actor
    with ActorLogging
    with Timers {
  private val cluster = Cluster(context.system)
  private val http = Http()(context.system)

  Cluster(context.system).subscribe(context.self, classOf[ClusterEvent.MemberUp], classOf[ClusterEvent.MemberRemoved])

  private lazy val sslContext = {
    val certificates = PemManagersProvider.loadCertificates(settings.apiCaPath)
    val factory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm)
    val keyStore = KeyStore.getInstance("PKCS12")
    keyStore.load(null)
    factory.init(keyStore, Array.empty)
    val km: Array[KeyManager] = factory.getKeyManagers
    val tm: Array[TrustManager] =
      PemManagersProvider.buildTrustManagers(certificates)
    val random: SecureRandom = new SecureRandom
    val sslContext = SSLContext.getInstance("TLSv1.2")
    sslContext.init(km, tm, random)
    sslContext
  }
  private val clientSslContext: Option[HttpsConnectionContext] =
    if (settings.secure) Some(ConnectionContext.httpsClient(sslContext)) else None

  implicit val dispatcher: ExecutionContextExecutor = context.system.dispatcher
  def receive = idle(0, SortedSet.empty(Member.ageOrdering))

  private def idle(deletionCost: Int, membersByAgeDesc: SortedSet[Member]): Receive = {
    case cs @ ClusterEvent.CurrentClusterState(members, _, _, _, _) =>
      log.debug("Received CurrentClusterState {}", cs)
      updateIfNewCost(deletionCost, membersByAgeDesc ++ members) // ordering used is from the first operand (so, by age)

    case ClusterEvent.MemberUp(m) =>
      log.debug("Received MemberUp {}", m)
      updateIfNewCost(deletionCost, membersByAgeDesc + m)

    case ClusterEvent.MemberRemoved(m, _) =>
      log.debug("Received MemberRemoved {}", m)
      updateIfNewCost(deletionCost, membersByAgeDesc - m)

    case PodAnnotated =>
      log.debug("Annotation updated successfully to {}", deletionCost)
      // cancelling an eventual retry in case the annotation succeeded in the meantime
      timers.cancel(RetryTimerId)

    case ScheduleRetry(ex) =>
      log.error(s"Failed to update annotation: [$ex]. Retrying with fixed delay of {}.", costSettings.retryDelay)
      timers.startSingleTimer(RetryTimerId, RetryAnnotate, costSettings.retryDelay)
      context.become(underRetryBackoff(membersByAgeDesc))

    case GiveUp(er: String) =>
      log.error(
        "There was a client error when trying to set pod-deletion-cost annotation. " +
        "Not retrying, check configuration. Error: {}",
        er)

    case es => log.debug("Ignoring message {}", es)
  }

  private def underRetryBackoff(membersByAgeDesc: SortedSet[Member]): Receive = {
    case ClusterEvent.MemberUp(m) =>
      log.debug("Received while on retry backoff MemberUp {}", m)
      context.become(underRetryBackoff(membersByAgeDesc + m))

    case ClusterEvent.MemberRemoved(m, _) =>
      log.debug("Received while on retry backoff MemberRemoved {}", m)
      context.become(underRetryBackoff(membersByAgeDesc - m))

    case RetryAnnotate =>
      updateIfNewCost(Int.MinValue, membersByAgeDesc)

    case es => log.debug("Under retry backoff, ignoring message {}", es)
  }

  private def updateIfNewCost(existingCost: Int, membersByAgeDesc: immutable.SortedSet[Member]): Unit = {

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
        "Updating pod-deletion-cost annotation for pod: [{}] with cost: [{}]. Namespace: [{}]",
        settings.podName,
        newCost,
        podNamespace
      )
      val request = ApiRequests.podDeletionCost(settings, apiToken, podNamespace, newCost)
      val response =
        clientSslContext.map(http.singleRequest(request, _)).getOrElse(http.singleRequest(request))

      toResult(response)(context.system).pipeTo(self)
      context.become(idle(newCost, membersByAgeDesc))
    } else context.become(idle(existingCost, membersByAgeDesc))
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

  private def toResult(futResponse: Future[HttpResponse])(implicit system: ActorSystem): Future[RequestResult] = {
    import system.dispatcher
    futResponse
      .map {
        case HttpResponse(status, _, e, _) if status.isSuccess() =>
          e.discardBytes()
          PodAnnotated
        case HttpResponse(s @ ClientError(_), _, e, _) =>
          e.discardBytes()
          GiveUp(s.toString())
        case HttpResponse(status, _, e, _) =>
          e.discardBytes()
          ScheduleRetry(s"Request failed with status=$status")
      }
      .recover {
        case NonFatal(e) => ScheduleRetry(e.getMessage)
      }
  }
}
