/*
 * Copyright (C) 2017-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.management.pod

import akka.actor.{Actor, ActorLogging}
import akka.cluster.{Cluster, ClusterEvent, Member}
import akka.event.Logging
import akka.http.scaladsl.model.HttpMethods.PATCH
import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.model._
import akka.http.scaladsl.{ConnectionContext, Http, HttpsConnectionContext}
import akka.pki.kubernetes.PemManagersProvider
import akka.util.ByteString

import java.nio.file.{Files, Paths}
import java.security.{KeyStore, SecureRandom}
import javax.net.ssl.{KeyManager, KeyManagerFactory, SSLContext, TrustManager}
import scala.collection.immutable.{SortedSet, TreeSet}
import scala.collection.immutable
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}
import scala.util.control.NonFatal


class KubernetesPodAnnotator(val podName: String) extends Actor with ActorLogging {
  private val cluster = Cluster(context.system)
  private val k8sSettings = PodDeletionCostSettings(context.system)

  private val http = Http()(context.system)
  private lazy val apiToken = readConfigVarFromFilesystem(k8sSettings.apiTokenPath, "api-token").getOrElse("")
  private val podNamespace = k8sSettings.podNamespace
    .orElse(readConfigVarFromFilesystem(k8sSettings.podNamespacePath, "namespace"))
    .getOrElse("default")

  /**
   * This uses blocking IO, and so should only be used to read configuration at startup.
   */
  private def readConfigVarFromFilesystem(path: String, name: String): Option[String] = {
    val file = Paths.get(path)
    if (Files.exists(file)) {
      try {
        Some(new String(Files.readAllBytes(file), "utf-8"))
      } catch {
        case NonFatal(e) =>
          log.error(e, "Error reading {} from {}", name, path)
          None
      }
    } else {
      log.warning("Unable to read {} from {} because it doesn't exist.", name, path)
      None
    }
  }

  private val clientSslContext: HttpsConnectionContext = ConnectionContext.httpsClient(sslContext)

  private lazy val sslContext = {
    val certificates = PemManagersProvider.loadCertificates(k8sSettings.apiCaPath)
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


  implicit val orderByAgeDesc: Ordering[Member] = Member.ageOrdering
  def receive = onMessage(0, SortedSet.empty)

  private def onMessage(deletionCost: Int, membersByAgeDesc: SortedSet[Member]): Receive = {
    case ClusterEvent.MemberUp(m) =>
      log.debug("Received MemberUp {}", m)
      updateDeletionCost(deletionCost, membersByAgeDesc + m)
    case ClusterEvent.MemberRemoved(m, _) =>
      log.debug("Received MemberRemoved {}", m)
      updateDeletionCost(deletionCost, membersByAgeDesc - m)
    case _ =>
  }

  private def updateDeletionCost(existingCost: Int,
                                 membersByAgeDesc: immutable.SortedSet[Member],
                                 costStrategy: CostStrategy = OlderCostsMore): Unit = {
    val newCost: Int = costStrategy.costOf(cluster.selfMember, membersByAgeDesc).getOrElse(0)
    log.debug("Calculated cost={} for member={} in members by age (desc): {}", newCost, cluster.selfMember, membersByAgeDesc)

    if (newCost != existingCost) {
      log.debug(
        "Updating pod-deletion-cost annotation for pod: [{}] with cost: [{}] (previously: {}). Namespace: [{}]",
        podName, newCost, existingCost, podNamespace)

      val request = podDeletionCostRequest(podNamespace, podName, newCost)

      val responseFuture: Future[HttpResponse] = if (k8sSettings.secure)
        http.singleRequest(request, clientSslContext)
      else
        http.singleRequest(request)

      val res = Await.result(responseFuture, 2.seconds)
      if (res.status.isSuccess()) {
        log.debug("Annotation updated successfully was {} now {}", existingCost, newCost)
        context.become(onMessage(newCost, membersByAgeDesc))
      } else {
        // FIXME retry?
        log.error(s"Failed to update annotation: ${res.status.intValue()} ${res.status.reason()}")
        context.become(onMessage(existingCost, membersByAgeDesc))
      }
    } else context.become(onMessage(existingCost, membersByAgeDesc))
  }

  private def podDeletionCostRequest(namespace: String, pod: String, cost: Int): HttpRequest = {
    val path = Uri.Path.Empty / "api" / "v1" / "namespaces" / namespace / "pods" / pod
    val scheme = if (k8sSettings.secure) "https" else "http"
    val uri = Uri.from(scheme, host = k8sSettings.apiServiceHost, port = k8sSettings.apiServicePort.toInt)
      .withPath(path)
    val headers = if (k8sSettings.secure) immutable.Seq(Authorization(OAuth2BearerToken(apiToken))) else Nil

    HttpRequest(
      method = PATCH,
      uri = uri,
      headers = headers,
      entity = HttpEntity(MediaTypes.`application/merge-patch+json`, ByteString(
        s"""{"metadata": {"annotations": {"controller.kubernetes.io/pod-deletion-cost": "$cost" }}}"""
      )))
  }
}
