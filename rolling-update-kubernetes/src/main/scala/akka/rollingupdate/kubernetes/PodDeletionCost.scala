/*
 * Copyright (C) 2017-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.rollingupdate.kubernetes

import akka.actor.ActorSystem
import akka.actor.ClassicActorSystemProvider
import akka.actor.ExtendedActorSystem
import akka.actor.Extension
import akka.actor.ExtensionId
import akka.actor.ExtensionIdProvider
import akka.actor.Props
import akka.annotation.InternalApi
import akka.event.Logging
import akka.rollingupdate.kubernetes.PodDeletionCost.Internal.BootstrapStep
import akka.rollingupdate.kubernetes.PodDeletionCost.Internal.Initializing
import akka.rollingupdate.kubernetes.PodDeletionCost.Internal.NotRunning

import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.control.NonFatal

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

      implicit val blockingDispatcher: ExecutionContext = system.dispatchers.lookup("blocking-io-dispatcher")
      val props = for {
        apiToken: String <- Future { readConfigVarFromFilesystem(k8sSettings.apiTokenPath, "api-token").getOrElse("") }
        podNamespace: String <- Future {
          k8sSettings.namespace
            .orElse(readConfigVarFromFilesystem(k8sSettings.namespacePath, "namespace"))
            .getOrElse("default")
        }
      } yield Props(classOf[PodDeletionCostAnnotator], k8sSettings, apiToken, podNamespace, costSettings)

      props.foreach(system.systemActorOf(_, "podDeletionCostAnnotator"))
    } else log.warning("PodDeletionCost extension already initiated, yet start() method was called again. Ignoring.")
  }

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

  // autostart if the extension is loaded through the config extension list
  private val autostart =
    system.settings.config.getStringList("akka.extensions").contains(classOf[PodDeletionCost].getName)

  if (autostart) {
    log.info("PodDeletionCost loaded through 'akka.extensions' auto-starting itself.")
    import system.dispatcher
    Future {
      try {
        PodDeletionCost(system).start()
      } catch {
        case NonFatal(ex) =>
          log.error(ex, "Failed to autostart PodDeletionCost extension")
      }
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
