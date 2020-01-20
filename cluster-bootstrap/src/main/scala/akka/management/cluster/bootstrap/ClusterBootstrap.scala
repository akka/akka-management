/*
 * Copyright (C) 2017-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.management.cluster.bootstrap

import java.util.concurrent.atomic.AtomicReference

import akka.AkkaVersion

import scala.concurrent.{ Future, Promise, TimeoutException }
import scala.concurrent.duration._
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
import akka.management.scaladsl.ManagementRouteProviderSettings
import akka.management.scaladsl.ManagementRouteProvider
import akka.pattern.pipe

final class ClusterBootstrap(implicit system: ExtendedActorSystem) extends Extension with ManagementRouteProvider {

  import ClusterBootstrap.Internal._
  import system.dispatcher

  private val log = Logging(system, classOf[ClusterBootstrap])

  private final val bootstrapStep = new AtomicReference[BootstrapStep](NotRunning)

  AkkaVersion.require("cluster-bootstrap", "2.5.19")

  val settings: ClusterBootstrapSettings = ClusterBootstrapSettings(system.settings.config, log)

  // used for initial discovery of contact points
  lazy val discovery: ServiceDiscovery =
    settings.contactPointDiscovery.discoveryMethod match {
      case "akka.discovery" =>
        val discovery = Discovery(system).discovery
        log.info("Bootstrap using default `akka.discovery` method: {}", Logging.simpleName(discovery))
        discovery

      case otherDiscoveryMechanism =>
        log.info("Bootstrap using `akka.discovery` method: {}", otherDiscoveryMechanism)
        Discovery(system).loadServiceDiscovery(otherDiscoveryMechanism)
    }

  private val joinDecider: JoinDecider = {
    system.dynamicAccess
      .createInstanceFor[JoinDecider](
        settings.joinDecider.implClass,
        List((classOf[ActorSystem], system), (classOf[ClusterBootstrapSettings], settings))
      )
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
        Cluster(system).settings.SeedNodes.mkString("[", ", ", "]")
      )
    } else if (bootstrapStep.compareAndSet(NotRunning, Initializing)) {
      log.info("Initiating bootstrap procedure using {} method...", settings.contactPointDiscovery.discoveryMethod)

      ensureSelfContactPoint()
      val bootstrapProps = BootstrapCoordinator.props(discovery, joinDecider, settings)
      val bootstrap = system.systemActorOf(bootstrapProps, "bootstrapCoordinator")
      // Bootstrap already logs in several other execution points when it can't form a cluster, and why.
      selfContactPoint.foreach {
        uri => bootstrap ! BootstrapCoordinator.Protocol.InitiateBootstrapping(uri)
      }
    } else log.warning("Bootstrap already initiated, yet start() method was called again. Ignoring.")

  /**
   * INTERNAL API
   *
   * We give the required selfContactPoint some time to be set asynchronously, or else log an error.
   */
  @InternalApi private[bootstrap] def ensureSelfContactPoint(): Unit = system.scheduler.scheduleOnce(10.seconds) {
    if (!selfContactPoint.isCompleted) {
      _selfContactPointUri.failure(new TimeoutException("Awaiting Bootstrap.selfContactPoint timed out."))
      log.error(
        "'Bootstrap.selfContactPoint' was NOT set, but is required for the bootstrap to work " +
        "if binding bootstrap routes manually and not via akka-management."
      )
    }
  }

  /**
   * INTERNAL API
   *
   * Must be invoked by whoever starts the HTTP server with the `HttpClusterBootstrapRoutes`.
   * This allows us to "reverse lookup" from a lowest-address sorted contact point list,
   * that we discover via discovery, if a given contact point corresponds to our remoting address,
   * and if so, we may opt to join ourselves using the address.
   */
  @InternalApi
  private[akka] def setSelfContactPoint(baseUri: Uri): Unit =
    _selfContactPointUri.success(baseUri)

  /** INTERNAL API */
  @InternalApi private[akka] def selfContactPoint: Future[Uri] = _selfContactPointUri.future
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
  }

}
