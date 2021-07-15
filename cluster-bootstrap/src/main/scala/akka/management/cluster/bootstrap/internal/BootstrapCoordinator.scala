/*
 * Copyright (C) 2017-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.management.cluster.bootstrap.internal

import java.time.LocalDateTime
import java.util.concurrent.ThreadLocalRandom

import scala.collection.immutable

import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.Address
import akka.actor.DeadLetterSuppression
import akka.actor.Props
import akka.actor.Status.Failure
import akka.actor.Timers
import akka.annotation.InternalApi
import akka.cluster.Cluster
import akka.discovery.{ Lookup, ServiceDiscovery }
import akka.discovery.ServiceDiscovery.ResolvedTarget
import akka.http.scaladsl.model.Uri
import akka.management.cluster.bootstrap.ClusterBootstrapSettings
import akka.management.cluster.bootstrap.JoinDecider
import akka.management.cluster.bootstrap.JoinDecision
import akka.management.cluster.bootstrap.JoinOtherSeedNodes
import akka.management.cluster.bootstrap.JoinSelf
import akka.management.cluster.bootstrap.KeepProbing
import akka.management.cluster.bootstrap.SeedNodesInformation
import akka.management.cluster.bootstrap.SeedNodesObservation
import akka.pattern.pipe
import scala.concurrent.duration._
import scala.util.Try

import akka.event.Logging
import akka.management.cluster.bootstrap.BootstrapLogMarker

/** INTERNAL API */
@InternalApi
private[akka] object BootstrapCoordinator {

  def props(discovery: ServiceDiscovery, joinDecider: JoinDecider, settings: ClusterBootstrapSettings): Props =
    Props(new BootstrapCoordinator(discovery, joinDecider, settings))

  object Protocol {
    final case class InitiateBootstrapping(selfContactPoint: Uri)

    final case class ObtainedHttpSeedNodesObservation(
        observedAt: LocalDateTime,
        contactPoint: ResolvedTarget,
        seedNodesSourceAddress: Address,
        observedSeedNodes: Set[Address],
        eligible: Boolean
    ) extends DeadLetterSuppression

    final case class ProbingFailed(contactPoint: ResolvedTarget, cause: Throwable) extends DeadLetterSuppression
  }

  private case object DiscoverTick extends DeadLetterSuppression
  private case object DecideTick extends DeadLetterSuppression

  protected[bootstrap] final case class ServiceContactsObservation(
      observedAt: LocalDateTime,
      observedContactPoints: Set[ResolvedTarget]
  ) {

    def membersChanged(other: ServiceContactsObservation): Boolean =
      this.observedContactPoints != other.observedContactPoints

    def sameOrChanged(other: ServiceContactsObservation): ServiceContactsObservation =
      if (membersChanged(other)) other
      else this
  }

  private[akka] def selectHosts(
      lookup: Lookup,
      fallbackPort: Int,
      filterOnFallbackPort: Boolean,
      contactPoints: immutable.Seq[ResolvedTarget]
  ): immutable.Iterable[ResolvedTarget] = {

    // if the user has specified a port name in the search, don't do any filtering and assume it
    // is handled in the service discovery mechanism
    if (lookup.portName.isDefined || !filterOnFallbackPort) {
      contactPoints
    } else {
      contactPoints.groupBy(_.host).flatMap {
        case (_, immutable.Seq(singleResult)) =>
          immutable.Seq(singleResult)
        case (_, multipleResults) =>
          if (multipleResults.exists(_.port.isDefined)) {
            multipleResults.filter(_.port.contains(fallbackPort))
          } else {
            multipleResults
          }
      }
    }
  }

}

/**
 * Looks up members of the same "service" via service discovery and initiates [[HttpContactPointBootstrap]]'s for each such node.
 *
 * If contact points do not return any seed-nodes for a `contactPointNoSeedsStableMargin` amount of time,
 * we decide that apparently there is no cluster formed yet in this deployment and someone has to become the first node
 * to join itself (becoming the first node of the cluster, that all other nodes will join).
 *
 * The decision of joining "self" is made by the [[JoinDecider]].
 *
 * If this node is the one joining itself other nodes will notice this via the contact-point probing mechanism
 * and join this node. Please note while the cluster is "growing" more nodes become aware of the cluster and
 * start returning the seed-nodes in their contact-points, thus the joining
 * process becomes somewhat "epidemic". Other nodes may get to know about this cluster by contacting any other node
 * that has joined it already, and they may join any seed-node that they retrieve using this method, as effectively
 * this will mean it joins the "right" cluster.
 *
 * CAVEATS:
 * There is a slight timing issue, that may theoretically appear in this bootstrap process.
 * One such example is the timing between the lookups becoming an "stable observation",
 * so an decision is made with regards to joining (e.g. self-joining) on the nodes,
 * yet exactly right-after the discoverContactPoints returned, a new node with lowest address appears (though is not observed).
 * Technically this is a race and "wrong decision" made by then, however since the node will itself also probe
 * the other nodes visible in discovery, it should soon realise that a cluster exists/is-forming and join that
 * one instead of self-joining, so the race in most timing situations should be harmless, however remains possible
 * under very unlucky timing -- where the new lowest node would not observe the new cluster being formed within the
 * stable timeout.
 */
// also known as the "Baron von Bootstrappen"
/** INTERNAL API */
@InternalApi
private[akka] class BootstrapCoordinator(
    discovery: ServiceDiscovery,
    joinDecider: JoinDecider,
    settings: ClusterBootstrapSettings
) extends Actor
    with Timers {

  import BootstrapCoordinator.Protocol._
  import BootstrapCoordinator._

  implicit private val ec = context.dispatcher
  private val log = Logging.withMarker(this)
  private val cluster = Cluster(context.system)
  private val DiscoverTimerKey = "resolve-key"
  private val DecideTimerKey = "decide-key"

  private val lookup = Lookup(
    settings.contactPointDiscovery.effectiveName(context.system),
    settings.contactPointDiscovery.portName,
    settings.contactPointDiscovery.protocol
  )

  private var lastContactsObservation: Option[ServiceContactsObservation] = None
  private var seedNodesObservations: Map[ResolvedTarget, SeedNodesObservation] = Map.empty

  private var decisionInProgress = false
  def startPeriodicDecisionTimer(): Unit =
    timers.startTimerWithFixedDelay(DecideTimerKey, DecideTick, settings.contactPoint.probeInterval)

  private var discoveryFailedBackoffCounter = 0
  def resetDiscoveryInterval(): Unit =
    discoveryFailedBackoffCounter = 0
  def backoffDiscoveryInterval(): Unit = {
    discoveryFailedBackoffCounter += 1
  }
  private[akka] def backedOffInterval(
      restartCount: Int,
      minBackoff: FiniteDuration,
      maxBackoff: FiniteDuration,
      randomFactor: Double
  ): FiniteDuration = {
    val rnd = 1.0 + ThreadLocalRandom.current().nextDouble() * randomFactor
    val calculatedDuration = Try(maxBackoff.min(minBackoff * math.pow(2, restartCount)) * rnd).getOrElse(maxBackoff)
    calculatedDuration match {
      case f: FiniteDuration => f
      case _                 => maxBackoff
    }
  }
  def startSingleDiscoveryTimer(): Unit = {
    val interval = backedOffInterval(
      discoveryFailedBackoffCounter,
      settings.contactPointDiscovery.interval,
      settings.contactPointDiscovery.exponentialBackoffMax,
      settings.contactPointDiscovery.exponentialBackoffRandomFactor
    )
    timers.startSingleTimer(DiscoverTimerKey, DiscoverTick, interval)
  }

  override def preStart(): Unit = {
    startSingleDiscoveryTimer()
    startPeriodicDecisionTimer()
  }

  /** Awaiting initial signal to start the bootstrap process */
  override def receive: Receive = {
    case InitiateBootstrapping(selfContactPoint) =>
      log.info(
        BootstrapLogMarker.init,
        "Locating service members. Using discovery [{}], join decider [{}], scheme [{}]",
        discovery.getClass.getName,
        joinDecider.getClass.getName,
        selfContactPoint.scheme
      )
      discoverContactPoints()
      context.become(bootstrapping(sender(), selfContactPoint.scheme))
  }

  /** In process of searching for seed-nodes */
  def bootstrapping(replyTo: ActorRef, selfContactPointScheme: String): Receive = {
    case DiscoverTick =>
      // the next round of discovery will be performed once this one returns
      discoverContactPoints()

    case ServiceDiscovery.Resolved(_, contactPoints) =>
      val filteredContactPoints: Iterable[ResolvedTarget] = selectHosts(
        lookup,
        settings.contactPoint.fallbackPort,
        settings.contactPoint.filterOnFallbackPort,
        contactPoints
      )

      log.info(
        BootstrapLogMarker.resolved(formatContactPoints(filteredContactPoints)),
        "Located service members based on: [{}]: [{}], filtered to [{}]",
        lookup,
        contactPoints.mkString(", "),
        formatContactPoints(filteredContactPoints).mkString(", ")
      )
      onContactPointsResolved(filteredContactPoints, selfContactPointScheme)

      resetDiscoveryInterval() // in case we were backed-off, we reset back to healthy intervals
      startSingleDiscoveryTimer() // keep looking in case other nodes join the discovery

    case ex: Failure =>
      log.warning(BootstrapLogMarker.resolveFailed, "Resolve attempt failed! Cause: {}", ex.cause)
      // prevent join decision until successful discoverContactPoints
      lastContactsObservation = None
      backoffDiscoveryInterval()
      startSingleDiscoveryTimer()

    case ObtainedHttpSeedNodesObservation(observedAt, contactPoint, infoFromAddress, observedSeedNodes, eligible) =>
      lastContactsObservation.foreach { contacts =>
        if (contacts.observedContactPoints.contains(contactPoint)) {
          log.info(
            BootstrapLogMarker.seedNodes(observedSeedNodes),
            "Contact point [{}] returned [{}] seed-nodes [{}]",
            infoFromAddress,
            observedSeedNodes.size,
            observedSeedNodes.mkString(", ")
          )

          seedNodesObservations = seedNodesObservations.updated(
            contactPoint,
            new SeedNodesObservation(observedAt, contactPoint, infoFromAddress, observedSeedNodes, eligible)
          )
        }

        // if we got seed nodes it is likely that it should join those immediately
        if (observedSeedNodes.nonEmpty)
          decide()
      }

    case DecideTick =>
      decide()

    case d: JoinDecision =>
      decisionInProgress = false
      d match {
        case KeepProbing => // continue scheduled lookups and probing of discovered contact points
        case JoinOtherSeedNodes(seedNodes) =>
          if (seedNodes.nonEmpty) {
            log.info(
              BootstrapLogMarker.join(seedNodes),
              "Joining [{}] to existing cluster [{}]",
              cluster.selfAddress,
              seedNodes.mkString(", "))

            val seedNodesList = (seedNodes - cluster.selfAddress).toList // order doesn't matter
            cluster.joinSeedNodes(seedNodesList)

            // once we issued a join bootstrapping is completed
            context.stop(self)
          }
        case JoinSelf =>
          log.info(
            BootstrapLogMarker.joinSelf,
            "Initiating new cluster, self-joining [{}]. " +
            "Other nodes are expected to locate this cluster via continued contact-point probing.",
            cluster.selfAddress
          )

          cluster.join(cluster.selfAddress)

          // once we issued a join bootstrapping is completed
          context.stop(self)
      }

    case ProbingFailed(contactPoint, _) =>
      lastContactsObservation.foreach { contacts =>
        if (contacts.observedContactPoints.contains(contactPoint)) {
          log.info(
            BootstrapLogMarker.seedNodesProbingFailed(formatContactPoints(contacts.observedContactPoints)),
            "Received signal that probing has failed, scheduling contact point probing again"
          )
          // child actor will have terminated now, so we ride on another discovery round to cause looking up
          // target nodes and if the same still exists, that would cause probing it again
          //
          // we do this in order to not keep probing nodes which simply have been removed from the deployment
        }
      }

      // remove the previous observation since it might be obsolete
      seedNodesObservations -= contactPoint
      startSingleDiscoveryTimer()
  }

  private def formatContactPoints(filteredContactPoints: Iterable[ResolvedTarget]): Iterable[String] = {
    filteredContactPoints.map(r => s"${r.host}:${r.port.getOrElse("0")}")
  }

  private def discoverContactPoints(): Unit = {
    log.info("Looking up [{}]", lookup)
    discovery.lookup(lookup, settings.contactPointDiscovery.resolveTimeout).pipeTo(self)
  }

  private def onContactPointsResolved(contactPoints: Iterable[ResolvedTarget], selfContactPointScheme: String): Unit = {
    val newObservation = ServiceContactsObservation(timeNow(), contactPoints.toSet)
    lastContactsObservation match {
      case Some(contacts) => lastContactsObservation = Some(contacts.sameOrChanged(newObservation))
      case None           => lastContactsObservation = Some(newObservation)
    }

    // remove observations from contact points that are not included any more
    seedNodesObservations = seedNodesObservations.filterNot {
      case (contactPoint, _) => !newObservation.observedContactPoints.contains(contactPoint)
    }

    // TODO stop the obsolete children (they are stopped when probing fails for too long)

    newObservation.observedContactPoints.foreach(ensureProbing(selfContactPointScheme, _))
  }

  private[internal] def ensureProbing(
      selfContactPointScheme: String,
      contactPoint: ResolvedTarget): Option[ActorRef] = {
    val targetPort = contactPoint.port.getOrElse(settings.contactPoint.fallbackPort)
    val rawBaseUri = Uri(selfContactPointScheme, Uri.Authority(Uri.Host(contactPoint.host), targetPort))
    val baseUri = settings.managementBasePath.fold(rawBaseUri)(prefix => rawBaseUri.withPath(Uri.Path(s"/$prefix")))

    val childActorName = HttpContactPointBootstrap.name(baseUri.authority.host, baseUri.authority.port)
    log.debug("Ensuring probing actor: " + childActorName)

    // This should never really happen in well configured env, but it may happen that someone is confused with ports
    // and we end up trying to probe (using http for example) a port that actually is our own remoting port.
    // We actively bail out of this case and log a warning instead.
    val wasAboutToProbeSelfAddress =
      baseUri.authority.host.address() == cluster.selfAddress.host.getOrElse("---") &&
      baseUri.authority.port == cluster.selfAddress.port.getOrElse(-1)

    if (wasAboutToProbeSelfAddress) {
      log.warning(
        "Misconfiguration detected! Attempted to start probing a contact-point which address [{}] " +
        "matches our local remoting address [{}]. Avoiding probing this address. Consider double checking your service " +
        "discovery and port configurations.",
        baseUri,
        cluster.selfAddress
      )
      None
    } else
      context.child(childActorName) match {
        case Some(contactPointProbingChild) =>
          Some(contactPointProbingChild)
        case None =>
          val props = HttpContactPointBootstrap.props(settings, contactPoint, baseUri)
          Some(context.actorOf(props, childActorName))
      }
  }

  private def decide(): Unit = {
    if (decisionInProgress)
      log.debug("Previous decision still in progress")
    else {
      lastContactsObservation.foreach { contacts =>
        val currentTime = timeNow()

        // filter out old observations, in case the probing failures are not triggered
        def isObsolete(obs: SeedNodesObservation): Boolean =
          java.time.Duration
            .between(obs.observedAt, currentTime)
            .toMillis > settings.contactPoint.probingFailureTimeout.toMillis

        // filter out ineligible observations - those that don't have formNewCluster enabled
        // @TODO this should be configurable
        def isIneligible(obs: SeedNodesObservation): Boolean =
          !obs.eligible

        val seedObservations = seedNodesObservations.valuesIterator.filterNot(isObsolete).filterNot(isIneligible).toSet
        val info =
          new SeedNodesInformation(currentTime, contacts.observedAt, contacts.observedContactPoints, seedObservations)

        decisionInProgress = true

        joinDecider
          .decide(info)
          .recover {
            case e =>
              log.error(e, "Join decision failed: {}", e)
              KeepProbing
          }
          .foreach(self ! _)
      }
    }
  }

  protected def timeNow(): LocalDateTime =
    LocalDateTime.now()

}
