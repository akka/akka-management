/*
 * Copyright (C) 2017-2023 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.rollingupdate.kubernetes

import akka.util.Version
import akka.actor.ActorSystem
import akka.actor.ExtendedActorSystem
import akka.actor.Extension
import akka.actor.ExtensionId
import akka.actor.ExtensionIdProvider
import akka.cluster.Cluster
import akka.event.Logging

import java.util.concurrent.atomic.AtomicBoolean
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.Promise
import scala.util.control.NonFatal

final class AppVersionRevision(implicit system: ExtendedActorSystem) extends Extension {

  private val log = Logging(system, classOf[AppVersionRevision])
  private val configPath = "akka.rollingupdate.kubernetes"
  private val config = system.settings.config.getConfig(configPath)
  private val k8sSettings = KubernetesSettings(config)
  implicit private val ec: ExecutionContext = system.dispatcher
  private final val isInitialized = new AtomicBoolean(false)
  log.debug("Settings {}", k8sSettings)

  private val versionPromise = Promise[Version]()

  def getRevision(): Future[Version] = versionPromise.future

  def start(): Unit = {
    if (isInitialized.compareAndSet(false, true)) {
      if (k8sSettings.podName.isEmpty) {
        val msg = "Not able to read the app version from the revision of the current ReplicaSet. Reason:" +
          "No configuration found to extract the pod name from. " +
          s"Be sure to provide the pod name with `$configPath.pod-name` " +
          "or by setting ENV variable `KUBERNETES_POD_NAME`."
        log.error(msg)
        versionPromise.failure(new IllegalStateException(msg))
      } else {
        Cluster(system).setAppVersionLater(getRevision())
        KubernetesApiImpl(log, k8sSettings).foreach { kubernetesApi =>
          versionPromise.completeWith(kubernetesApi.readRevision().map(Version(_)))
        }
      }
    } else {
      log.warning("AppVersionRevision extension already initiated, yet start() method was called again. Ignoring.")
    }

  }

  // autostart if the extension is loaded through the config extension list
  private val autostart =
    system.settings.config.getStringList("akka.extensions").contains(classOf[AppVersionRevision].getName)

  if (autostart) {
    log.info("AppVersionRevision loaded through 'akka.extensions' auto-starting itself.")
    try {
      start()
    } catch {
      case NonFatal(ex) =>
        log.error(ex, "Failed to autostart AppVersionRevision extension")
    }
  }
}

object AppVersionRevision extends ExtensionId[AppVersionRevision] with ExtensionIdProvider {

  override def lookup: AppVersionRevision.type = AppVersionRevision

  override def createExtension(system: ExtendedActorSystem): AppVersionRevision = new AppVersionRevision()(system)

  override def get(system: ActorSystem): AppVersionRevision = super.get(system)

}
