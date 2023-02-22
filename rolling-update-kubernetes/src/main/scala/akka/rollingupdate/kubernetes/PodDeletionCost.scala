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
import akka.cluster.Cluster
import akka.cluster.ClusterEvent
import akka.event.Logging
import akka.rollingupdate.kubernetes.PodDeletionCost.Internal.BootstrapStep
import akka.rollingupdate.kubernetes.PodDeletionCost.Internal.Initializing
import akka.rollingupdate.kubernetes.PodDeletionCost.Internal.NotRunning

import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.Future
import scala.util.control.NonFatal

final class PodDeletionCost(implicit system: ExtendedActorSystem) extends Extension {

  import system.dispatcher

  private val log = Logging(system, classOf[PodDeletionCost])

  private val settings = KubernetesSettings(system)
  log.debug("Settings {}", settings)

  private final val startStep = new AtomicReference[BootstrapStep](NotRunning)

  def start(): Unit = {
    if (settings.podName.isEmpty) {
      log.warning(
        "No configuration found to extract the pod name from. " +
        "Be sure to provide the pod name with `akka.management.pod-deletion-cost.pod-name` " +
        "or by setting ENV variable `KUBERNETES_POD_NAME`.")
    } else if (startStep.compareAndSet(NotRunning, Initializing)) {
      val listener = system.systemActorOf(Props[PodDeletionCostAnnotator](), "podDeletionCostAnnotator")
      Cluster(system).subscribe(listener, classOf[ClusterEvent.MemberUp])
      Cluster(system).subscribe(listener, classOf[ClusterEvent.MemberRemoved])
    } else log.warning("PodDeletionCost extension already initiated, yet start() method was called again. Ignoring.")
  }

  // autostart if the extension is loaded through the config extension list
  private val autostart =
    system.settings.config.getStringList("akka.extensions").contains(classOf[PodDeletionCost].getName)

  if (autostart) {
    log.info("PodDeletionCost loaded through 'akka.extensions' auto-starting itself.")
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
  private[kubernetes] object Internal {
    sealed trait BootstrapStep
    case object NotRunning extends BootstrapStep
    case object Initializing extends BootstrapStep
  }

}
