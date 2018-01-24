/*
 * Copyright (C) 2017 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.management.cluster.bootstrap.dns

import java.util.Date

import akka.actor.Status.Failure
import akka.actor.{ Actor, ActorLogging, ActorRef, ActorSystem, Address, DeadLetterSuppression, Props, Timers }
import akka.annotation.InternalApi
import akka.cluster.Cluster
import akka.discovery.SimpleServiceDiscovery
import akka.discovery.SimpleServiceDiscovery.ResolvedTarget
import akka.http.scaladsl.model.Uri
import akka.management.cluster.bootstrap.{ ClusterBootstrap, ClusterBootstrapSettings }
import akka.pattern.pipe
import akka.util.PrettyDuration

import scala.collection.immutable
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Try

/** INTERNAL API */
@InternalApi
private[bootstrap] object HeadlessServiceDnsBootstrap {

  def props(discovery: SimpleServiceDiscovery, settings: ClusterBootstrapSettings): Props =
    Props(new HeadlessServiceDnsBootstrap(discovery, settings))

  object Protocol {
    final case object InitiateBootstraping
    sealed trait BootstrapingCompleted
    final case object BootstrapingCompleted extends BootstrapingCompleted

    final case class ObtainedHttpSeedNodesObservation(
        seedNodesSourceAddress: Address,
        observedSeedNodes: Set[Address]
    ) extends DeadLetterSuppression

    final case class NoSeedNodesObtainedWithinDeadline(contactPoint: Uri) extends DeadLetterSuppression

    object Internal {
      final case class AttemptResolve(serviceName: String) extends DeadLetterSuppression
    }
  }

  protected[dns] final case class DnsServiceContactsObservation(
      observedAt: Long,
      observedContactPoints: List[ResolvedTarget]
  ) {

    /** Prepares member addresses for a self-join attempt */
    def selfAddressIfAbleToJoinItself(system: ActorSystem): Option[Address] = {
      val cluster = Cluster(system)
      val bootstrap = ClusterBootstrap(system)

      // we KNOW this await is safe, since we set the value before we bind the HTTP things even
      val selfContactPoint = Try(Await.result(bootstrap.selfContactPoint, 10.second)).getOrElse(throw new Exception(
            "Bootstrap.selfContactPoint was NOT set! This is required for the bootstrap to work! " +
            "If binding bootstrap routes manually and not via akka-management"))

      // we check if a contact point is "us", by comparing host and port that we've bound to
      def lowestContactPointIsSelfManagement(lowest: ResolvedTarget): Boolean =
        lowest.host == selfContactPoint.authority.host.toString() &&
        lowest.port.getOrElse(selfContactPoint.authority.port) == selfContactPoint.authority.port

      lowestAddressContactPoint
        .find(lowestContactPointIsSelfManagement) // if the lowest contact-point address is "us"
        .map(_ ⇒ cluster.selfAddress) // then we should join our own remoting address
    }

    /**
     * Contact point with the "lowest" contact point address,
     * it is expected to join itself if no other cluster is found in the deployment.
     */
    def lowestAddressContactPoint: Option[ResolvedTarget] =
      observedContactPoints.sortBy(e ⇒ e.host + ":" + e.port.getOrElse(0)).headOption

    def willBeStableAt(settings: ClusterBootstrapSettings): Long =
      observedAt + settings.contactPointDiscovery.stableMargin.toMillis

    def isPastStableMargin(settings: ClusterBootstrapSettings, timeNow: Long): Boolean =
      willBeStableAt(settings) < timeNow

    def durationSinceObservation(timeNowMillis: Long): Duration = {
      val millisSince = timeNowMillis - observedAt
      math.max(0, millisSince).millis
    }

    def membersChanged(other: DnsServiceContactsObservation): Boolean = {
      val these = this.observedContactPoints.toSet
      val others = other.observedContactPoints.toSet
      others != these
    }

    def sameOrChanged(other: DnsServiceContactsObservation): DnsServiceContactsObservation =
      if (membersChanged(other)) other
      else this
  }

}

/**
 * Looks up members of the same "service" in DNS and initiates [[HttpContactPointBootstrap]]'s for each such node.
 * If any of the contact-points returns a list of seed nodes it joins them immediately.
 *
 * If contact points do not return any seed-nodes for a `contactPointNoSeedsStableMargin` amount of time,
 * we decide that apparently there is no cluster formed yet in this deployment and someone as to become the first node
 * to join itself (becoming the first node of the cluster, that all other nodes will join).
 *
 * The decision of joining "self" is made by deterministically sorting the discovered service IPs
 * and picking the *lowest* address.
 *
 * If this node is the one with the lowest address in the deployment, it will join itself and other nodes will notice
 * this via the contact-point probing mechanism and join this node. Please note while the cluster is "growing"
 * more nodes become aware of the cluster and start returning the seed-nodes in their contact-points, thus the joining
 * process becomes somewhat "epidemic". Other nodes may get to know about this cluster by contacting any other node
 * that has joined it already, and they may join any seed-node that they retrieve using this method, as effectively
 * this will mean it joins the "right" cluster.
 *
 * CAVEATS:
 * There is a slight timing issue, that may theoretically appear in this bootstrap process.
 * FIXME explain the races
 */
// also known as the "Baron von Bootstrappen"
final class HeadlessServiceDnsBootstrap(discovery: SimpleServiceDiscovery, settings: ClusterBootstrapSettings)
    extends Actor
    with ActorLogging
    with Timers {

  import HeadlessServiceDnsBootstrap.Protocol._
  import HeadlessServiceDnsBootstrap._
  import context.dispatcher

  private val cluster = Cluster(context.system)

  private val TimerKeyResolveDNS = "resolve-dns-key"

  private var lastContactsObservation: DnsServiceContactsObservation =
    DnsServiceContactsObservation(Long.MaxValue, Nil)

  /** Awaiting initial signal to start the bootstrap process */
  override def receive: Receive = {
    case InitiateBootstraping ⇒
      val serviceName = settings.contactPointDiscovery.effectiveName(context.system)

      log.info("Locating service members; Lookup: {}", serviceName)
      discovery.lookup(serviceName, settings.contactPointDiscovery.resolveTimeout).pipeTo(self)

      context become bootstraping(serviceName, sender())
  }

  /** In process of searching for seed-nodes */
  def bootstraping(serviceName: String, replyTo: ActorRef): Receive = {
    case Internal.AttemptResolve(name) ⇒
      discovery.lookup(name, settings.contactPointDiscovery.resolveTimeout).pipeTo(self)

    case SimpleServiceDiscovery.Resolved(name, contactPoints) ⇒
      onContactPointsResolved(name, contactPoints)

    case ex: Failure ⇒
      log.warning("Resolve attempt failed! Cause: {}", ex.cause)
      scheduleNextResolve(serviceName, settings.contactPointDiscovery.interval)

    case ObtainedHttpSeedNodesObservation(infoFromAddress, observedSeedNodes) ⇒
      log.info("Contact point [{}] returned [{}] seed-nodes [{}], initiating cluster joining...", infoFromAddress,
        observedSeedNodes.size, observedSeedNodes.mkString(", "))

      replyTo ! BootstrapingCompleted

      val seedNodesList = observedSeedNodes.toList
      cluster.joinSeedNodes(seedNodesList)

      // once we issued a join bootstraping is completed
      context.stop(self)

    case NoSeedNodesObtainedWithinDeadline(contactPoint) ⇒
      log.info(
          "Contact point [{}] exceeded stable margin with no seed-nodes in sight. " +
          "Considering whether this node is allowed to JOIN itself to initiate a new cluster.", contactPoint)

      onNoSeedNodesObtainedWithinStableDeadline(contactPoint)
  }

  private def onContactPointsResolved(serviceName: String, contactPoints: immutable.Seq[ResolvedTarget]): Unit = {
    val newObservation = DnsServiceContactsObservation(timeNow(), contactPoints.toList)
    lastContactsObservation = lastContactsObservation.sameOrChanged(newObservation)

    if (contactPoints.size < settings.contactPointDiscovery.requiredContactPointsNr)
      onInsufficientContactPointsDiscovered(serviceName, lastContactsObservation)
    else
      onSufficientContactPointsDiscovered(serviceName, lastContactsObservation)
  }

  private def onInsufficientContactPointsDiscovered(serviceName: String,
                                                    observation: DnsServiceContactsObservation): Unit = {
    log.info("Discovered [{}] observation, which is less than the required [{}], retrying (interval: {})",
      observation.observedContactPoints.size, settings.contactPointDiscovery.requiredContactPointsNr,
      PrettyDuration.format(settings.contactPointDiscovery.interval))

    scheduleNextResolve(serviceName, settings.contactPointDiscovery.interval)
  }

  private def onSufficientContactPointsDiscovered(serviceName: String,
                                                  observation: DnsServiceContactsObservation): Unit = {
    log.info("Initiating contact-point probing, sufficient contact points: {}",
      observation.observedContactPoints.mkString(", "))

    observation.observedContactPoints.foreach { contactPoint ⇒
      val targetPort = contactPoint.port.getOrElse(settings.contactPoint.fallbackPort)
      val rawBaseUri = Uri("http", Uri.Authority(Uri.Host(contactPoint.host), targetPort))
      val baseUri = settings.managementBasePath.fold(rawBaseUri)(prefix => rawBaseUri.withPath(Uri.Path(s"/$prefix")))
      ensureProbing(baseUri)
    }
  }

  private def onNoSeedNodesObtainedWithinStableDeadline(contactPoint: Uri): Unit = {
    val dnsRecordsAreStable = lastContactsObservation.isPastStableMargin(settings, timeNow())
    if (dnsRecordsAreStable) {
      lastContactsObservation.selfAddressIfAbleToJoinItself(context.system) match {
        case Some(allowedToJoinSelfAddress) ⇒
          log.info(
              "Initiating new cluster, self-joining [{}], as this node has the LOWEST address out of: [{}]! " +
              "Other nodes are expected to locate this cluster via continued contact-point probing.",
              cluster.selfAddress, lastContactsObservation.observedContactPoints)

          cluster.join(allowedToJoinSelfAddress)

          context.stop(self) // the bootstraping is complete
        case None ⇒
          log.info(
              "Exceeded stable margins without locating seed-nodes, however this node is NOT the lowest address out " +
              "of the discovered IPs in this deployment, thus NOT joining self. Expecting node {} (out of {}) to perform the self-join " +
              "and initiate the cluster.", lastContactsObservation.lowestAddressContactPoint,
              lastContactsObservation.observedContactPoints)

        // nothing to do anymore, the probing will continue until the lowest addressed node decides to join itself.
        // note, that due to DNS changes this may still become this node! We'll then await until the dns stableMargin
        // is exceeded and would decide to try joining self again (same code-path), that time successfully though.
      }

    } else {
      // TODO throttle this logging? It may be caused by any of the probing actors
      log.debug(
          "DNS observation has changed more recently than the dnsStableMargin({}) allows (at: {}), not considering to join myself. " +
          "This process will be retried.", settings.contactPointDiscovery.stableMargin,
          new Date(lastContactsObservation.observedAt))
    }
  }

  private def ensureProbing(baseUri: Uri): Option[ActorRef] = {
    val childActorName = s"contactPointProbe-${baseUri.authority.host}-${baseUri.authority.port}"
    log.info("Ensuring probing actor: " + childActorName)

    // This should never really happen in well configured env, but it may happen that someone is confused with ports
    // and we end up trying to probe (using http for example) a port that actually is our own remoting port.
    // We actively bail out of this case and log a warning instead.
    val wasAboutToProbeSelfAddress =
      baseUri.authority.host.address() == cluster.selfAddress.host.getOrElse("---") &&
      baseUri.authority.port == cluster.selfAddress.port.getOrElse(-1)

    if (wasAboutToProbeSelfAddress) {
      log.warning("Misconfiguration detected! Attempted to start probing a contact-point which address [{}] " +
        "matches our local remoting address [{}]. Avoiding probing this address. Consider double checking your service " +
        "discovery and port configurations.", baseUri, cluster.selfAddress)
      None
    } else
      context.child(childActorName) match {
        case Some(contactPointProbingChild) ⇒
          Some(contactPointProbingChild)
        case None ⇒
          val props = HttpContactPointBootstrap.props(settings, self, baseUri)
          Some(context.actorOf(props, childActorName))
      }
  }

  private def scheduleNextResolve(serviceName: String, interval: FiniteDuration): Unit =
    timers.startSingleTimer(TimerKeyResolveDNS, Internal.AttemptResolve(serviceName), interval)

  protected def timeNow(): Long =
    System.currentTimeMillis()

}
