/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.cluster.bootstrap.dns

import java.util.{ Date, Locale }

import akka.actor.Status.Failure
import akka.actor.{ Actor, ActorLogging, ActorRef, ActorSystem, Address, DeadLetterSuppression, Props, Timers }
import akka.annotation.InternalApi
import akka.cluster.Cluster
import akka.cluster.ClusterEvent.CurrentClusterState
import akka.cluster.bootstrap.ClusterBootstrapSettings
import akka.http.scaladsl.model.Uri
import akka.pattern.{ ask, pipe }
import akka.discovery.ServiceDiscovery
import akka.discovery.ServiceDiscovery.ResolvedTarget
import akka.util.PrettyDuration

import scala.collection.immutable
import scala.concurrent.Future
import scala.concurrent.duration._

/** INTERNAL API */
@InternalApi
private[bootstrap] object HeadlessServiceDnsBootstrap {

  def props(discovery: ServiceDiscovery, settings: ClusterBootstrapSettings): Props =
    Props(new HeadlessServiceDnsBootstrap(discovery, settings))

  object Protocol {
    final case object InitiateBootstraping
    sealed trait BootstrapingCompleted
    final case object BootstrapingCompleted extends BootstrapingCompleted

    final case class ObtainedHttpSeedNodesObservation(
        seedNodesSourceAddress: Address,
        observedSeedNodes: Set[Address] // TODO order by address sorting?
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
      val selfHost = cluster.selfAddress.host
      if (lowestAddressContactPoint.contains(selfHost.get)) {
        // we are the "lowest address" and should join ourselves to initiate a new cluster
        Some(cluster.selfAddress)
      } else None

    }

    /** Contact point with the "lowest" address, it is expected to join itself if no other cluster is found in the deployment. */
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
@InternalApi
final class HeadlessServiceDnsBootstrap(discovery: ServiceDiscovery, settings: ClusterBootstrapSettings)
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

      log.info("Locating service members, via DNS lookup: {}", serviceName)
      discovery.lookup(serviceName, settings.contactPointDiscovery.resolveTimeout).pipeTo(self)

      context become bootstraping(serviceName, sender())
  }

  /** In process of searching for seed-nodes */
  def bootstraping(serviceName: String, replyTo: ActorRef): Receive = {
    case Internal.AttemptResolve(name) ⇒
      discovery.lookup(name, settings.contactPointDiscovery.resolveTimeout).pipeTo(self)

    case ServiceDiscovery.Resolved(name, contactPoints) ⇒
      onContactPointsResolved(name, contactPoints)

    case ex: Failure ⇒
      log.warning("Resolve attempt failed! Cause: {}", ex.cause)
      scheduleNextResolve(serviceName, settings.contactPointDiscovery.interval)

    case ObtainedHttpSeedNodesObservation(infoFromAddress, observedSeedNodes) ⇒
      log.info("Contact point [{}] returned [{}] seed-nodes [{}], initiating cluster joining...", infoFromAddress,
        observedSeedNodes.size, observedSeedNodes)

      replyTo ! BootstrapingCompleted

      val seedNodesList = observedSeedNodes.toList
      cluster.joinSeedNodes(seedNodesList)

      // once we issued a join bootstraping is completed
      context.stop(self)

    case NoSeedNodesObtainedWithinDeadline(contactPoint) ⇒
      log.info(
          "Contact point [{}] exceeded stable margin with no seed-nodes in sight. " +
          "Considering weather this node is allowed to JOIN itself to initiate a new cluster.",
          contactPoint) // TODO debug

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
      val baseUri = Uri("http", Uri.Authority(Uri.Host(contactPoint.host), targetPort))
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
          log.debug(
              "Exceeded stable margins without locating seed-nodes, however this node is NOT the lowest address out " +
              "of the discovered IPs in this deployment, thus NOT joining self. Expecting node ({}) to perform the self-join " +
              "and initiate the cluster.", lastContactsObservation.lowestAddressContactPoint)

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

  private def ensureProbing(baseUri: Uri): ActorRef = {
    val childActorName = s"contactPointProbe-${baseUri.authority.host}-${baseUri.authority.port}"
    log.info("Ensuring probing actor: " + childActorName)
    context.child(childActorName) match {
      case Some(contactPointProbingChild) ⇒
        contactPointProbingChild
      case None ⇒
        val props = HttpContactPointBootstrap.props(settings, self, baseUri)
        context.actorOf(props, childActorName)
    }
  }

  private def scheduleNextResolve(serviceName: String, interval: FiniteDuration): Unit =
    timers.startSingleTimer(TimerKeyResolveDNS, Internal.AttemptResolve(serviceName), interval)

  protected def timeNow(): Long =
    System.currentTimeMillis()

}
