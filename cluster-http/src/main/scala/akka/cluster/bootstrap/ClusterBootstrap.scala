/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.cluster.bootstrap

import java.util.concurrent.atomic.AtomicReference

import akka.actor.{ ActorSystem, ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider }
import akka.cluster.bootstrap.dns.HeadlessServiceDnsBootstrap
import akka.discovery.ServiceDiscovery
import akka.event.Logging
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.util.Timeout

import scala.concurrent.duration._
import scala.util.{ Failure, Success }

final class ClusterBootstrap(implicit system: ExtendedActorSystem) extends Extension {
  import ClusterBootstrap._
  import system.dispatcher
  private implicit val mat = ActorMaterializer()(system)

  private val log = Logging(system, classOf[ClusterBootstrap])

  private final val bootstrapStep = new AtomicReference[BootstrapStep](NotRunning)

  val settings = ClusterBootstrapSettings(system.settings.config)

  // used for initial discovery of contact points
  val discovery: ServiceDiscovery = {
    val clazz = settings.contactPointDiscovery.discoveryClass
    system.dynamicAccess.createInstanceFor[ServiceDiscovery](clazz, List(classOf[ActorSystem] → system)).get
  }

  def start(): Unit =
    if (bootstrapStep.compareAndSet(NotRunning, Initializing)) {
      log.info("Initiating bootstrap procedure using {} method...", settings.contactPointDiscovery.discoveryMethod)

      // TODO this could be configured as well, depending on how we want to bootstrap
      val bootstrapProps = HeadlessServiceDnsBootstrap.props(discovery, settings)
      val bootstrap = system.systemActorOf(bootstrapProps, "headlessServiceDnsBootstrap")

      // the boot timeout not really meant to be exceeded
      implicit val bootTimeout: Timeout = Timeout(1.day)
      val bootstrapCompleted = (bootstrap ? HeadlessServiceDnsBootstrap.Protocol.InitiateBootstraping).mapTo[
          HeadlessServiceDnsBootstrap.Protocol.BootstrapingCompleted]

      bootstrapCompleted.onComplete {
        case Success(_) ⇒ // ignore, all's fine
        case Failure(_) ⇒ log.warning("Failed to complete bootstrap within {}!", bootTimeout)
      }

    }

}

object ClusterBootstrap extends ExtensionId[ClusterBootstrap] with ExtensionIdProvider {
  override def lookup: ClusterBootstrap.type = ClusterBootstrap

  override def get(system: ActorSystem): ClusterBootstrap = super.get(system)

  override def createExtension(system: ExtendedActorSystem): ClusterBootstrap = new ClusterBootstrap()(system)

  private[bootstrap] sealed trait BootstrapStep
  private[bootstrap] case object NotRunning extends BootstrapStep
  private[bootstrap] case object Initializing extends BootstrapStep
  // TODO get the Initialized state once done
  private[bootstrap] case object Initialized extends BootstrapStep

}
