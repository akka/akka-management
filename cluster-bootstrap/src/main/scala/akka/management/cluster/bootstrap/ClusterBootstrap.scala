/*
 * Copyright (C) 2017-2018 Lightbend Inc. <http://www.lightbend.com>
 */

package akka.management.cluster.bootstrap

import java.util.concurrent.atomic.AtomicReference

import akka.AkkaVersion

import scala.concurrent.Future
import scala.concurrent.Promise
import akka.actor.ActorSystem
import akka.actor.ExtendedActorSystem
import akka.actor.Extension
import akka.actor.ExtensionId
import akka.actor.ExtensionIdProvider
import akka.annotation.InternalApi
import akka.cluster.Cluster
import akka.discovery.{ Discovery, ServiceDiscovery }
import akka.event.Logging
import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.server.Route
import akka.management.cluster.bootstrap.contactpoint.HttpClusterBootstrapRoutes
import akka.management.cluster.bootstrap.internal.BootstrapCoordinator
import akka.management.http.ManagementRouteProvider
import akka.management.http.ManagementRouteProviderSettings

final class ClusterBootstrap(implicit system: ExtendedActorSystem) extends Extension with ManagementRouteProvider {

  import ClusterBootstrap.Internal._
  import system.dispatcher

  private val log = Logging(system, classOf[ClusterBootstrap])

  private final val bootstrapStep = new AtomicReference[BootstrapStep](NotRunning)

  AkkaVersion.require("cluster-bootstrap", "2.5.19")

  val settings: ClusterBootstrapSettings = ClusterBootstrapSettings(system.settings.config, log)

  // used for initial discovery of contact points
  val discovery: ServiceDiscovery =
    settings.contactPointDiscovery.discoveryMethod match {
      case "akka.discovery" ⇒
        val discovery = Discovery(system).discovery
        log.info("Bootstrap using default `akka.discovery` method: {}", Logging.simpleName(discovery))
        discovery

      case otherDiscoveryMechanism ⇒
        log.info("Bootstrap using `akka.discovery` method: {}", otherDiscoveryMechanism)
        Discovery(system).loadServiceDiscovery(otherDiscoveryMechanism)
    }

  private val joinDecider: JoinDecider = {
    system.dynamicAccess
      .createInstanceFor[JoinDecider](settings.joinDecider.implClass,
        List((classOf[ActorSystem], system), (classOf[ClusterBootstrapSettings], settings)))
      .get
  }

  private[this] val _selfContactPointUri: Promise[Uri] = Promise()

  override def routes(routeProviderSettings: ManagementRouteProviderSettings): Route = {
    log.info(s"Using self contact point address: ${routeProviderSettings.selfBaseUri}")
    this.setSelfContactPoint(routeProviderSettings.selfBaseUri)

    new HttpClusterBootstrapRoutes(settings).routes
  }

  def start(): Unit =
    if (Cluster(system).settings.SeedNodes.nonEmpty) {
      log.warning(
          "Application is configured with specific `akka.cluster.seed-nodes`: {}, bailing out of the bootstrap process! " +
          "If you want to use the automatic bootstrap mechanism, make sure to NOT set explicit seed nodes in the configuration. " +
          "This node will attempt to join the configured seed nodes.",
          Cluster(system).settings.SeedNodes.mkString("[", ", ", "]"))
    } else if (bootstrapStep.compareAndSet(NotRunning, Initializing)) {
      log.info("Initiating bootstrap procedure using {} method...", settings.contactPointDiscovery.discoveryMethod)

      val bootstrapProps = BootstrapCoordinator.props(discovery, joinDecider, settings)
      val bootstrap = system.systemActorOf(bootstrapProps, "bootstrapCoordinator")
      // Bootstrap already logs in several other execution points when it can't form a cluster, and why.
      bootstrap ! BootstrapCoordinator.Protocol.InitiateBootstrapping
    } else log.warning("Bootstrap already initiated, yet start() method was called again. Ignoring.")

  /**
   * INTERNAL API
   *
   * Must be invoked by whoever starts the HTTP server with the `HttpClusterBootstrapRoutes`.
   * This allows us to "reverse lookup" from a lowest-address sorted contact point list,
   * that we discover via discovery, if a given contact point corresponds to our remoting address,
   * and if so, we may opt to join ourselves using the address.
   *
   * @return true if successfully set, false otherwise (i.e. was set already)
   */
  @InternalApi
  private[akka] def setSelfContactPoint(baseUri: Uri): Unit =
    _selfContactPointUri.success(baseUri)

  /** INTERNAL API */
  @InternalApi private[akka] def selfContactPoints: Future[Set[(String, Int)]] =
    _selfContactPointUri.future.map { uri =>
      settings.joinDecider.selfDerivedHost match {
        case Some(selfDerivedHost) if uri.authority.host.isIPv4 =>
          val derivedHost = s"${uri.authority.host.toString.replace('.', '-')}.$selfDerivedHost"
          log.info(s"Derived self contact point $derivedHost:${uri.authority.port}")
          Set((uri.authority.host.toString, uri.authority.port), (derivedHost, uri.authority.port))
        case _ =>
          Set((uri.authority.host.toString, uri.authority.port))
      }
    }
}

object ClusterBootstrap extends ExtensionId[ClusterBootstrap] with ExtensionIdProvider {

  override def lookup: ClusterBootstrap.type = ClusterBootstrap

  override def get(system: ActorSystem): ClusterBootstrap = super.get(system)

  override def createExtension(system: ExtendedActorSystem): ClusterBootstrap = new ClusterBootstrap()(system)

  /**
   * INTERNAL API
   */
  private[bootstrap] object Internal {
    sealed trait BootstrapStep
    case object NotRunning extends BootstrapStep
    case object Initializing extends BootstrapStep
    // TODO get the Initialized state once done
    case object Initialized extends BootstrapStep
  }

}
