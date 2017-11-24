/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.cluster.bootstrap

import java.util.concurrent.atomic.AtomicReference

import akka.Done
import akka.actor.{ ActorSystem, ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider }
import akka.cluster.Cluster
import akka.cluster.bootstrap.dns.HeadlessServiceDnsBootstrap
import akka.cluster.bootstrap.contactpoint.ClusterBootstrapRoutes
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.RouteResult
import akka.pattern.ask
import akka.discovery.ServiceDiscovery
import akka.stream.ActorMaterializer
import akka.util.Timeout

import scala.concurrent.duration._
import scala.concurrent.{ Future, Promise }

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

      val bootstrapProps = HeadlessServiceDnsBootstrap.props(settings)
      // TODO load the right one depending on config / env?
      // TODO should get it's own config then too
      val bootstrap = system.systemActorOf(bootstrapProps, "headlessServiceDnsBootstrap")
      (bootstrap ? HeadlessServiceDnsBootstrap.Protocol.InitiateBootstraping).mapTo[
          HeadlessServiceDnsBootstrap.Protocol.BootstrapingCompleted]

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
