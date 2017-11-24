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
  // FIXME should bind and handle together with the other tools, like akka-cluster-http management somehow
  private implicit val http = Http(system)

  private val log = Logging(system, classOf[ClusterBootstrap])

  private final val bootstrapStep = new AtomicReference[BootstrapStep](NotRunning)

  val settings = ClusterBootstrapSettings(system.settings.config)

  // used for initial discovery of contact points
  val discovery: ServiceDiscovery = {
    val clazz = settings.contactPointDiscovery.discoveryClass
    system.dynamicAccess.createInstanceFor[ServiceDiscovery](clazz, List(classOf[ActorSystem] → system)).get
  }

  def start(): Future[Done] = {
    val p = Promise[Done]()
    if (bootstrapStep.compareAndSet(NotRunning, Initializing(p.future))) {
      log.info("Initiating bootstrap procedure using {} method...", settings.contactPointDiscovery.discoveryMethod)
      implicit val initTimeout: Timeout = Timeout(1.day) // configure? or what should it do really? try until forever meh?

      val initCompleted = Promise[Done]()

      // TODO load the right one depending on config / env?
      // TODO should get it's own config then too
      val bootstrapManager =
        system.systemActorOf(HeadlessServiceDnsBootstrap.props(settings), "headlessServiceDnsBootstrap")
      val reply = (bootstrapManager ? HeadlessServiceDnsBootstrap.Protocol.InitiateBootstraping).mapTo[
          HeadlessServiceDnsBootstrap.Protocol.BootstrapingCompleted]

      // FIXME should be the same server as akka-management
      val clusterBootstrapRoutes = new ClusterBootstrapRoutes(settings)

      val bindHost = Cluster(system).selfAddress.host.getOrElse(
          _ ⇒ throw new IllegalStateException("Node has no selfAddress.host!"))

      // TODO this is how we can discover the port we should be binding to
      discovery.lookup("_akka-bootstrap" + ".effective-name.default")
      // ----

      http.bindAndHandle(clusterBootstrapRoutes.routes, bindHost.toString,
        // FIXME we may want to DISCOVER our own port for this actually! it'd be set in the yml file, and told to us via DNS
        settings.contactPoint.portFallback)

      initCompleted.completeWith(reply.map(_ ⇒ Done)) // FIXME

      initCompleted.future
    } else
      bootstrapStep.get() match {
        case Initializing(whenBootstrapCompleted) ⇒
          val exception = new Exception("Bootstrap already in progress...")
          p.failure(exception) // no one has this future in hand, however we complete it to keep it hanging around
          Future.failed(exception)

          whenBootstrapCompleted

        case Initialized =>
          Future.successful(Done)
        case _ ⇒ Future.failed(new Exception("NOPE"))
      }
  }
}

object ClusterBootstrap extends ExtensionId[ClusterBootstrap] with ExtensionIdProvider {
  override def lookup: ClusterBootstrap.type = ClusterBootstrap

  override def get(system: ActorSystem): ClusterBootstrap = super.get(system)

  override def createExtension(system: ExtendedActorSystem): ClusterBootstrap = new ClusterBootstrap()(system)

  private[bootstrap] sealed trait BootstrapStep
  private[bootstrap] case object NotRunning extends BootstrapStep
  private[bootstrap] case class Initializing(whenBootstrapCompleted: Future[Done]) extends BootstrapStep
  private[bootstrap] case object Initialized extends BootstrapStep

}
