/*
 * Copyright (C) 2017-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.coordination.lease.kubernetes

import java.nio.file.Files
import java.nio.file.Paths
import java.security.KeyStore
import java.security.SecureRandom
import java.text.Normalizer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.control.NonFatal

import akka.actor.ActorRef
import akka.actor.ExtendedActorSystem
import akka.coordination.lease.LeaseException
import akka.coordination.lease.LeaseSettings
import akka.coordination.lease.LeaseTimeoutException
import akka.coordination.lease.kubernetes.KubernetesLease.makeDNS1039Compatible
import akka.coordination.lease.kubernetes.LeaseActor._
import akka.coordination.lease.kubernetes.internal.KubernetesApiImpl
import akka.coordination.lease.scaladsl.Lease
import akka.dispatch.Dispatchers.DefaultBlockingDispatcherId
import akka.event.Logging
import akka.http.scaladsl.ConnectionContext
import akka.http.scaladsl.HttpsConnectionContext
import akka.pattern.AskTimeoutException
import akka.pattern.ask
import akka.pki.kubernetes.PemManagersProvider
import akka.util.ConstantFun
import akka.util.Timeout
import javax.net.ssl.KeyManager
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager

object KubernetesLease {
  val configPath = "akka.coordination.lease.kubernetes"
  private val leaseCounter = new AtomicInteger(1)

  /**
   * Limit the length of a name to 63 characters.
   * Some subsystem of Kubernetes cannot manage longer names.
   */
  private def truncateTo63Characters(name: String): String = name.take(63)

  /**
   * Removes from the leading and trailing positions the specified characters.
   */
  private def trim(name: String, characters: List[Char]): String =
    name.dropWhile(characters.contains(_)).reverse.dropWhile(characters.contains(_)).reverse

  /**
   * Make a name compatible with DNS 1039 standard: like a single domain name segment.
   * Regex to follow: [a-z]([-a-z0-9]*[a-z0-9])
   * Limit the resulting name to 63 characters
   */
  private def makeDNS1039Compatible(name: String, allowLeaseNameTruncation: Boolean): String = {
    val normalized =
      Normalizer.normalize(name, Normalizer.Form.NFKD).toLowerCase.replaceAll("[_.]", "-").replaceAll("[^-a-z0-9]", "")

    if (allowLeaseNameTruncation)
      trim(truncateTo63Characters(normalized), List('-'))
    else {
      if (normalized.length > 63)
        throw new IllegalArgumentException(
          s"Too long lease resource name [$normalized]. At most 63 characters is accepted. " +
          "A custom resource name can be defined in configuration `lease-name`, ClusterSingletonSettings, " +
          "or ClusterShardingSettings. " +
          "For backwards compatibility, lease name truncation can be allowed by enabling config " +
          "`akka.coordination.lease.kubernetes.allow-lease-name-truncation`.")
      trim(normalized, List('-'))
    }
  }

}

class KubernetesLease private[akka] (system: ExtendedActorSystem, leaseTaken: AtomicBoolean, settings: LeaseSettings)
    extends Lease(settings) {

  private val log = Logging(system, classOf[KubernetesLease])

  private val k8sSettings = KubernetesSettings(settings.leaseConfig, settings.timeoutSettings)
  private val leaseName = makeDNS1039Compatible(settings.leaseName, k8sSettings.allowLeaseNameTruncation)
  private object Dispatchers {
    implicit val blocking: ExecutionContext = system.dispatchers.lookup(DefaultBlockingDispatcherId)
  }

  private def k8sApiToken(): Future[String] = {
    import Dispatchers.blocking
    Future {
      readConfigVarFromFilesystem(k8sSettings.apiTokenPath, "api-token").getOrElse("")
    }
  }

  private val k8sApi: Future[KubernetesApi] = {
    import Dispatchers.blocking
    for {
      namespace: String <- Future {
        k8sSettings.namespace
          .orElse(readConfigVarFromFilesystem(k8sSettings.namespacePath, "namespace"))
          .getOrElse("default")
      }
      httpsContext <- Future(clientHttpsConnectionContext())
    } yield {
      new KubernetesApiImpl(system, k8sSettings, namespace, k8sApiToken, httpsContext)
    }
  }
  private implicit val timeout: Timeout = Timeout(settings.timeoutSettings.operationTimeout)
  import system.dispatcher

  def this(leaseSettings: LeaseSettings, system: ExtendedActorSystem) =
    this(system, new AtomicBoolean(false), leaseSettings)

  private val leaseActor: Future[ActorRef] = {
    k8sApi.map { api =>
      log.debug(
        "Starting kubernetes lease actor [{}] for lease [{}], owner [{}]",
        leaseActor,
        leaseName,
        settings.ownerName)
      system.systemActorOf(
        LeaseActor.props(api, settings, leaseName, leaseTaken),
        s"kubernetesLease${KubernetesLease.leaseCounter.incrementAndGet}"
      )
    }
  }
  if (leaseName != settings.leaseName) {
    log.info("Original lease name [{}] sanitized for kubernetes: [{}]", settings.leaseName, leaseName)
  }

  override def checkLease(): Boolean = leaseTaken.get()

  override def release(): Future[Boolean] = {
    for {
      ref <- leaseActor
      response <- (ref ? Release()).recoverWith {
        case _: AskTimeoutException =>
          Future.failed(
            new LeaseTimeoutException(
              s"Timed out trying to release lease [${leaseName}, ${settings.ownerName}]. It may still be taken."))
      }
    } yield {
      response match {
        case LeaseReleased       => true
        case InvalidRequest(msg) => throw new LeaseException(msg)
      }
    }
  }

  override def acquire(): Future[Boolean] = {
    acquire(ConstantFun.scalaAnyToUnit)

  }
  override def acquire(leaseLostCallback: Option[Throwable] => Unit): Future[Boolean] = {
    for {
      ref <- leaseActor
      response <- (ref ? Acquire(leaseLostCallback)).recoverWith {
        case _: AskTimeoutException =>
          Future.failed[Boolean](
            new LeaseTimeoutException(
              s"Timed out trying to acquire lease [${leaseName}, ${settings.ownerName}]. It may still be taken."))
      }
    } yield {
      response match {
        case LeaseAcquired       => true
        case LeaseTaken          => false
        case InvalidRequest(msg) => throw new LeaseException(msg)
      }
    }
  }

  /**
   * This uses blocking IO, and so should only be used at startup from blocking dispatcher.
   */
  private def clientHttpsConnectionContext(): Option[HttpsConnectionContext] = {
    if (k8sSettings.secure) {
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
      Some(ConnectionContext.httpsClient(sslContext))
    } else
      None
  }

  /**
   * This uses blocking IO, and so should only be used to read configuration at startup from blocking dispatcher.
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
}
