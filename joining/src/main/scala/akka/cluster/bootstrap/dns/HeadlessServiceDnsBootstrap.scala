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
import akka.http.scaladsl.model.Uri
import akka.io.AsyncDnsResolver.SrvResolved
import akka.io.{ Dns, IO }
import akka.pattern.{ ask, pipe }
import ru.smslv.akka.dns.raw.SRVRecord

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
    }
  }

  protected[dns] final case class DnsServiceContactsObservation(
      observedAt: Long,
      observedContactPoints: List[SRVRecord] // TODO order by address sorting?
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
      val these = (this.observedContactPoints.toSet).map { r: SRVRecord ⇒
        s"${r.target}:${r.port}"
      }
      println("these >>> " + these)
      val others = (other.observedContactPoints.toSet).map { r: SRVRecord ⇒
        s"${r.target}:${r.port}"
      }
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

    case SrvResolved(name, records) ⇒
      val recordsSize = records.size
      log.info("Discovered {} [{}] observation: {}", recordsSize, name, records.mkString("[", ",", "]"))

      onSrvResolved(serviceName, records, recordsSize)

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

  private def onSrvResolved(serviceName: String, records: immutable.Seq[SRVRecord], recordsSize: Int): Unit =
    if (recordsSize < settings.requiredContactPoints) {
      val timeNow = System.currentTimeMillis() // TODO use Clock?
      val newObservation = DnsServiceContactsObservation(timeNow, records.toList)
      lastContactsObservation = lastContactsObservation.sameOrChanged(newObservation)

      val shouldInitiateJoining =
        lastContactsObservation.isPastStableMargin(settings, timeNow) || // observation is "stable enough", let's join
        recordsSize >= settings.expectedContactPoints // join fast-path, all expected contact points have been observed

      if (shouldInitiateJoining) onInsufficientContactPointsDiscovered(serviceName, lastContactsObservation)
      else onSufficientContactPointsDiscovered(serviceName, lastContactsObservation)
    }

  /**
   * Pipes the result of [[resolve]] ([[SrvResolved]]) to `self`.
   */
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

    observation.observedContactPoints.foreach { record ⇒
      val baseUri = Uri("http", Uri.Authority(Uri.Host(record.target), settings.contactPointPort))
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

  private def resolve(name: String, resolveTimeout: FiniteDuration): Future[SrvResolved] =
    dns.ask(Dns.Resolve(name))(resolveTimeout) map {
      case srv: SrvResolved =>
        log.warning("Resolved Srv.Resolved: " + srv)
        srv
      case resolved: Dns.Resolved =>
        log.warning("Resolved UNEXPECTED Dns.Resolved: " + resolved)
        SrvResolved(name, Nil)
      case resolved ⇒
        log.warning("Resolved UNEXPECTED: " + resolved)
        SrvResolved(name, Nil)
    }

  private def stopProbingContactPoints(): Unit =
    context.children foreach context.stop

}
