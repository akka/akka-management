/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.cluster.bootstrap.dns

import akka.actor.Status.Failure
import akka.actor.{ Actor, ActorLogging, ActorRef, Address, AddressFromURIString, Props, Timers }
import akka.annotation.InternalApi
import akka.cluster.{ Cluster, ClusterEvent }
import akka.cluster.ClusterEvent.{ ClusterDomainEvent, CurrentClusterState }
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
    final case object InitiateBootstraping // TODO service name may be a leak of the Kubernetes impl? or not really...
    final case class BootstrapingCompleted(state: CurrentClusterState)

    final case class ObtainedHttpSeedNodesObservation(
        seedNodesSourceAddress: Address,
        observedSeedNodes: Set[Address] // TODO order by address sorting?
    )

    final case class NoSeedNodesObtainedWithinDeadline()

    object Internal {
      final case class AttemptResolve(serviceName: String, resolveTimeout: FiniteDuration)
      final case class DnsResolvedTo(serviceName: String, addresses: immutable.Seq[String])
    }
  }

  protected[dns] final case class DnsServiceContactsObservation(
      observedAt: Long,
      observedContactPoints: List[String] // TODO order by address sorting?
  ) {

    def willBeStableAt(settings: ClusterBootstrapSettings): Long =
      observedAt + settings.stableMargin.toMillis

    def isPastStableMargin(settings: ClusterBootstrapSettings, timeNow: Long): Boolean =
      willBeStableAt(settings) >= timeNow

    def durationSinceObservation(timeNowMillis: Long): Duration = {
      val millisSince = timeNowMillis - observedAt
      math.max(0, millisSince).millis
    }

    def membersChanged(other: DnsServiceContactsObservation): Boolean = {
      // TODO a bit naive?
      val these = (this.observedContactPoints.toSet)
      println("these >>> " + these)
      val others = (other.observedContactPoints.toSet)
      println("others >>> " + others)
      others != these
    }

    def sameOrChanged(other: DnsServiceContactsObservation): DnsServiceContactsObservation =
      if (membersChanged(other)) other
      else this
  }

}

// also known as the Baron von Bootstrappen
class HeadlessServiceDnsBootstrap(settings: ClusterBootstrapSettings) extends Actor with ActorLogging with Timers {
  import HeadlessServiceDnsBootstrap._
  import HeadlessServiceDnsBootstrap.Protocol._
  import context.dispatcher

  val cluster = Cluster(context.system)
  private val TimerKeyResolveDNS = "resolve-dns-key"

  //  // FIXME it's either buggy or has some assumptions that we do not want
//  import com.lightbend.dns.locator.{ ServiceLocator ⇒ DnsServiceLocator }
//  val dnsLocator: ActorRef = context.actorOf(DnsServiceLocator.props, "dnsServiceLocator")

  private val dns = IO(Dns)(context.system)

  private var lastContactsObservation: DnsServiceContactsObservation =
    DnsServiceContactsObservation(Long.MaxValue, Nil)

  /** Awaiting initial */
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
      log.info("Discovered {} [{}] observation: {}", name, contactPoints.mkString("[", ", ", "]"))

      onContactPointsResolved(serviceName, contactPoints)

    case ex: Failure ⇒
      log.warning("Resolve attempt failed! Cause: {}", ex.cause)
      scheduleNextResolve(serviceName, settings.dnsResolveTimeout)

    case ObtainedHttpSeedNodesObservation(infoFromAddress, observedSeedNodes) ⇒
      log.info("Contact point [{}] returned [{}] seed nodes [{}], initiating cluster joining...", infoFromAddress,
        observedSeedNodes.size, observedSeedNodes)

      replyTo ! "JOINING SEED NODES!" // FIXME nicer typed message

      val seedNodesList = observedSeedNodes.toList
      cluster.joinSeedNodes(seedNodesList)

      stopProbingContactPoints()
      cluster.subscribe(self, ClusterEvent.InitialStateAsSnapshot, classOf[ClusterDomainEvent])
      context become joining(seedNodesList)

    case NoSeedNodesObtainedWithinDeadline() ⇒
    // this means that, likely, no cluster exists in this deployment yet, the lowest addressed node should join itself.

    // TODO as it is right now, this just means that one of the contact points was stable and did not return any seed nodes
    // TODO this is likely fine, but we could add additional guards here, like:
    // TODO  - like the dns IPs we get back also being stable for the stableMargin
    // TODO  - or all children we spawned having reported that their deadlines passed
    // this moment by definition "risky" though, so a matter of how much we think that would help
  }

  /** Awaiting joining to complete before shutting down the bootstrap actors */
  def joining(seedNodes: immutable.Seq[Address]): Receive = {
    case initialState: CurrentClusterState ⇒
      log.info("Received initial cluster state after joining seed-nodes: {}", initialState)
      log.info("Bootstrap completed, this node is now part of the cluster containing (leader) [{}]",
        initialState.leader)
      context stop self

    case outstandingObservation: ObtainedHttpSeedNodesObservation ⇒
    // we purposefully ignore additional seed-node observations received in this state,
    // since we already acted upon the first observation with a `joinSeedNodes`
    case _: NoSeedNodesObtainedWithinDeadline ⇒
    // ignore, same reason as above
  }

  private def onContactPointsResolved(serviceName: String, contactPoints: immutable.Seq[String]): Unit = {
    val timeNow = System.currentTimeMillis() // TODO use Clock?
    val newObservation = DnsServiceContactsObservation(timeNow, contactPoints.toList)
    lastContactsObservation = lastContactsObservation.sameOrChanged(newObservation)

    if (contactPoints.size < settings.expectedContactPoints)
      onInsufficientContactPointsDiscovered(serviceName, lastContactsObservation)
    else onSufficientContactPointsDiscovered(serviceName, lastContactsObservation)
  }

  private def onInsufficientContactPointsDiscovered(serviceName: String,
                                                    observation: DnsServiceContactsObservation): Unit = {
    log.info("Discovered ({}) observation, which is less than the required ({}), retrying (interval: {})",
      observation.observedContactPoints.size, settings.requiredContactPoints, settings.dnsResolveTimeout)

    scheduleNextResolve(serviceName, settings.dnsResolveTimeout)
  }

  private def onSufficientContactPointsDiscovered(serviceName: String,
                                                  observation: DnsServiceContactsObservation): Unit = {
    log.info("Initiating joining! Got sufficient contact points: {}",
      observation.observedContactPoints.mkString("[", ",", "]")) // FIXME better message

    observation.observedContactPoints.foreach { contactPoint ⇒
      val baseUri = Uri("http", Uri.Authority(Uri.Host(contactPoint), settings.contactPointPort))
      ensureProbing(baseUri)
    }
  }

  private def ensureProbing(baseUri: Uri): ActorRef = {
    val childActorName = s"ip-${baseUri.authority.host}-${baseUri.authority.port}"
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
    // TODO configure interval at which we DNS lookup
    timers.startSingleTimer(TimerKeyResolveDNS, Internal.AttemptResolve(serviceName, resolveTimeout), 1.second)

  private def resolve(name: String, resolveTimeout: FiniteDuration): Future[DnsResolvedTo] =
    dns.ask(Dns.Resolve(name))(resolveTimeout) map {
      case srv: SrvResolved =>
        log.debug("Resolved Srv.Resolved: " + srv)
        DnsResolvedTo(name, srv.srv.map(_.target))

      case resolved: Dns.Resolved =>
        log.debug("Resolved Dns.Resolved: " + resolved)
        DnsResolvedTo(name, resolved.ipv4.map(_.getHostAddress))

      case resolved ⇒
        log.warning("Resolved UNEXPECTED: " + resolved)
        DnsResolvedTo(name, Nil)
    }

  private def stopProbingContactPoints(): Unit =
    context.children foreach context.stop

}
