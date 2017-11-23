/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.cluster.bootstrap.dns

import java.util.Date

import akka.actor.Status.Failure
import akka.actor.{ Actor, ActorLogging, ActorRef, ActorSystem, Address, DeadLetterSuppression, Props, Timers }
import akka.annotation.InternalApi
import akka.cluster.Cluster
import akka.cluster.ClusterEvent.CurrentClusterState
import akka.cluster.bootstrap.ClusterBootstrapSettings
import akka.cluster.bootstrap.dns.HeadlessServiceDnsBootstrap.Protocol.Internal.DnsResolvedTo
import akka.http.scaladsl.model.Uri
import akka.io.AsyncDnsResolver.SrvResolved
import akka.io.{ Dns, IO }
import akka.pattern.{ ask, pipe }

import scala.collection.immutable
import scala.concurrent.Future
import scala.concurrent.duration._

/** INTERNAL API */
@InternalApi
private[bootstrap] object HeadlessServiceDnsBootstrap {

  def props(settings: ClusterBootstrapSettings): Props = Props(new HeadlessServiceDnsBootstrap(settings))

  object Protocol {
    final case object InitiateBootstraping
    final case class BootstrapingCompleted(state: CurrentClusterState)

    final case class ObtainedHttpSeedNodesObservation(
        seedNodesSourceAddress: Address,
        observedSeedNodes: Set[Address] // TODO order by address sorting?
    ) extends DeadLetterSuppression

    final case class NoSeedNodesObtainedWithinDeadline(contactPoint: Uri) extends DeadLetterSuppression

    object Internal {
      final case class AttemptResolve(serviceName: String, resolveTimeout: FiniteDuration)
          extends DeadLetterSuppression
      final case class DnsResolvedTo(serviceName: String, addresses: immutable.Seq[String])
          extends DeadLetterSuppression
    }
  }

  protected[dns] final case class DnsServiceContactsObservation(
      observedAt: Long,
      observedContactPoints: List[String] // TODO order by address sorting?
  ) {

    /** Prepares member addresses for a self-join attempt */
    def selfAddressIfAbleToJoinItself(system: ActorSystem): Option[Address] = {
      val cluster = Cluster(system)
      // TODO or should we sort the addresses "mathematically" rather than lexicographically?
      val selfHost = cluster.selfAddress.host
      if (lowestAddressContactPoint.contains(selfHost.get)) {
        // we are the "lowest address" and should join ourselves to initiate a new cluster
        Some(cluster.selfAddress)
      } else None

    }

    /** Contact point with the "lowest" address, it is expected to join itself if no other cluster is found in the deployment. */
    def lowestAddressContactPoint: Option[String] = observedContactPoints.sorted.headOption

    def willBeStableAt(settings: ClusterBootstrapSettings): Long =
      observedAt + settings.dnsStableMargin.toMillis

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
final class HeadlessServiceDnsBootstrap(settings: ClusterBootstrapSettings)
    extends Actor
    with ActorLogging
    with Timers {

  import HeadlessServiceDnsBootstrap.Protocol._
  import HeadlessServiceDnsBootstrap._
  import context.dispatcher

  private val dns = IO(Dns)(context.system)
  private val cluster = Cluster(context.system)

  private val TimerKeyResolveDNS = "resolve-dns-key"

  private var lastContactsObservation: DnsServiceContactsObservation =
    DnsServiceContactsObservation(Long.MaxValue, Nil)

  /** Awaiting initial signal to start the bootstrap process */
  override def receive: Receive = {
    case InitiateBootstraping ⇒
      val serviceName = settings.effectiveServiceName(context.system)

      log.info("Locating service members, via DNS lookup: {}", serviceName)
      resolve(serviceName, settings.dnsResolveTimeout).pipeTo(self)

      context become bootstraping(serviceName, sender())
  }

  /** In process of searching for seed-nodes */
  def bootstraping(serviceName: String, replyTo: ActorRef): Receive = {
    case Internal.AttemptResolve(name, timeout) ⇒
      resolve(name, timeout).pipeTo(self)

    case DnsResolvedTo(name, contactPoints) ⇒
      onContactPointsResolved(name, contactPoints)

    case ex: Failure ⇒
      log.warning("Resolve attempt failed! Cause: {}", ex.cause)
      scheduleNextResolve(serviceName, settings.dnsResolveTimeout)

    case ObtainedHttpSeedNodesObservation(infoFromAddress, observedSeedNodes) ⇒
      log.info("Contact point [{}] returned [{}] seed-nodes [{}], initiating cluster joining...", infoFromAddress,
        observedSeedNodes.size, observedSeedNodes)

      replyTo ! "JOINING SEED NODES!" // FIXME nicer typed message

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

  private def onContactPointsResolved(serviceName: String, contactPoints: immutable.Seq[String]): Unit = {
    val newObservation = DnsServiceContactsObservation(timeNow(), contactPoints.toList)
    lastContactsObservation = lastContactsObservation.sameOrChanged(newObservation)

    if (contactPoints.size < settings.requiredContactPointsNr)
      onInsufficientContactPointsDiscovered(serviceName, lastContactsObservation)
    else
      onSufficientContactPointsDiscovered(serviceName, lastContactsObservation)
  }

  private def onInsufficientContactPointsDiscovered(serviceName: String,
                                                    observation: DnsServiceContactsObservation): Unit = {
    log.info("Discovered ({}) observation, which is less than the required ({}), retrying (interval: {})",
      observation.observedContactPoints.size, settings.requiredContactPointsNr, settings.dnsResolveTimeout)

    scheduleNextResolve(serviceName, settings.dnsResolveTimeout)
  }

  private def onSufficientContactPointsDiscovered(serviceName: String,
                                                  observation: DnsServiceContactsObservation): Unit = {
    log.info("Initiating contact-point probing, sufficient contact points: {}",
      observation.observedContactPoints.mkString(", "))

    observation.observedContactPoints.foreach { contactPoint ⇒
      // TODO would be nice if we can discover this via DNS as well? and leave option to override here
      val targetPort = settings.httpContactPointPort
      val baseUri = Uri("http", Uri.Authority(Uri.Host(contactPoint), targetPort))
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
              "Other nodes are expected to locate this cluster via continued contact-point probing.")

          cluster.join(allowedToJoinSelfAddress)

          context.stop(self) // the bootstraping is complete
        case None ⇒
          log.info(
              "Exceeded stable margins without locating seed-nodes, however this node is NOT the lowest address out " +
              "of the discovered IPs in this deployment, thus NOT joining self. Expecting node ({}) to perform the self-join " +
              "and initiate the cluster.", lastContactsObservation.lowestAddressContactPoint)

        // nothing to do anymore, the probing will continue until the lowest addressed node decides to join itself.
        // note, that due to DNS changes this may still become this node! We'll then await until the dns stableMargin
        // is exceeded and would decide to try joining self again (same code-path), that time successfully though.
      }

    } else {
      // TODO throttle this logging? It may be caused by any of the probing actors
      log.info(
          "DNS observation has changed more recently than the dnsStableMargin({}) allows (at: {}), not considering to join myself. " +
          "This process will be retried.", settings.dnsStableMargin, new Date(lastContactsObservation.observedAt))
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

  private def scheduleNextResolve(serviceName: String, resolveTimeout: FiniteDuration): Unit =
    timers.startSingleTimer(TimerKeyResolveDNS, Internal.AttemptResolve(serviceName, resolveTimeout),
      settings.dnsResolveInterval)

  private def resolve(name: String, resolveTimeout: FiniteDuration): Future[DnsResolvedTo] = {
    def cleanIpString(ipString: String) =
      ipString.replaceAll("/", "")

    dns.ask(Dns.Resolve(name))(resolveTimeout) map {
      case srv: SrvResolved =>
        log.debug("Resolved Srv.Resolved: " + srv)
        DnsResolvedTo(name, srv.srv.map(_.target).map(cleanIpString))

      case resolved: Dns.Resolved =>
        log.debug("Resolved Dns.Resolved: " + resolved)
        DnsResolvedTo(name, resolved.ipv4.map(_.getHostAddress).map(cleanIpString))

      case resolved ⇒
        log.warning("Resolved UNEXPECTED: " + resolved)
        DnsResolvedTo(name, Nil)
    }
  }

  protected def timeNow(): Long =
    System.currentTimeMillis()

}
