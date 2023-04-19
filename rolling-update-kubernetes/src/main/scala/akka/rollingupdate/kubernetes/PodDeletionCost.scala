/*
 * Copyright (C) 2017-2023 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.rollingupdate.kubernetes

import java.nio.file.Files
import java.nio.file.Paths
import java.security.KeyStore
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicReference

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.control.NonFatal

import akka.actor.ActorSystem
import akka.actor.ClassicActorSystemProvider
import akka.actor.ExtendedActorSystem
import akka.actor.Extension
import akka.actor.ExtensionId
import akka.actor.ExtensionIdProvider
import akka.annotation.InternalApi
import akka.dispatch.Dispatchers.DefaultBlockingDispatcherId
import akka.event.Logging
import akka.http.scaladsl.ConnectionContext
import akka.http.scaladsl.HttpsConnectionContext
import akka.pki.kubernetes.PemManagersProvider
import akka.rollingupdate.kubernetes.PodDeletionCost.Internal.BootstrapStep
import akka.rollingupdate.kubernetes.PodDeletionCost.Internal.Initializing
import akka.rollingupdate.kubernetes.PodDeletionCost.Internal.NotRunning
import javax.net.ssl.KeyManager
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager

final class PodDeletionCost(implicit system: ExtendedActorSystem) extends Extension {

  private val log = Logging(system, classOf[PodDeletionCost])
  private val configPath = "akka.rollingupdate.kubernetes"
  private val config = system.settings.config.getConfig(configPath)
  private val k8sSettings = KubernetesSettings(config)
  private val costSettings = PodDeletionCostSettings(config)
  log.debug("Settings {}", k8sSettings)

  private final val startStep = new AtomicReference[BootstrapStep](NotRunning)

  def start(): Unit = {
    if (k8sSettings.podName.isEmpty) {
      log.warning(
        "No configuration found to extract the pod name from. " +
        s"Be sure to provide the pod name with `$configPath.pod-name` " +
        "or by setting ENV variable `KUBERNETES_POD_NAME`.")
    } else if (startStep.compareAndSet(NotRunning, Initializing)) {
      log.debug("Starting PodDeletionCost for podName={} with settings={}", k8sSettings.podName, costSettings)

      implicit val blockingDispatcher: ExecutionContext = system.dispatchers.lookup(DefaultBlockingDispatcherId)
      val props = for {
        apiToken: String <- Future { readConfigVarFromFilesystem(k8sSettings.apiTokenPath, "api-token").getOrElse("") }
        podNamespace: String <- Future {
          k8sSettings.namespace
            .orElse(readConfigVarFromFilesystem(k8sSettings.namespacePath, "namespace"))
            .getOrElse("default")
        }
        httpsContext <- Future(clientHttpsConnectionContext())
      } yield {
        val kubernetesApi = new KubernetesApiImpl(system, k8sSettings, podNamespace, apiToken, httpsContext)
        PodDeletionCostAnnotator.props(k8sSettings, costSettings, kubernetesApi)
      }

      props.foreach(system.systemActorOf(_, "podDeletionCostAnnotator"))
    } else log.warning("PodDeletionCost extension already initiated, yet start() method was called again. Ignoring.")
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

  // autostart if the extension is loaded through the config extension list
  private val autostart =
    system.settings.config.getStringList("akka.extensions").contains(classOf[PodDeletionCost].getName)

  if (autostart) {
    log.info("PodDeletionCost loaded through 'akka.extensions' auto-starting itself.")
    try {
      PodDeletionCost(system).start()
    } catch {
      case NonFatal(ex) =>
        log.error(ex, "Failed to autostart PodDeletionCost extension")
    }
  }
}

object PodDeletionCost extends ExtensionId[PodDeletionCost] with ExtensionIdProvider {

  override def lookup: PodDeletionCost.type = PodDeletionCost

  override def get(system: ActorSystem): PodDeletionCost = super.get(system)

  override def get(system: ClassicActorSystemProvider): PodDeletionCost = super.get(system)

  override def createExtension(system: ExtendedActorSystem): PodDeletionCost = new PodDeletionCost()(system)

  /**
   * INTERNAL API
   */
  @InternalApi private[kubernetes] object Internal {
    sealed trait BootstrapStep
    case object NotRunning extends BootstrapStep
    case object Initializing extends BootstrapStep
  }

}
