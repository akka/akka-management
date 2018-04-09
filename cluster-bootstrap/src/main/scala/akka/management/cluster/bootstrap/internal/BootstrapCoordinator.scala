/*
 * Copyright (C) 2017 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.management.cluster.bootstrap.internal

import java.time.Duration
import java.time.LocalDateTime

import scala.collection.immutable

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.Address
import akka.actor.DeadLetterSuppression
import akka.actor.Props
import akka.actor.Status.Failure
import akka.actor.Timers
import akka.annotation.InternalApi
import akka.cluster.Cluster
import akka.discovery.SimpleServiceDiscovery
import akka.discovery.SimpleServiceDiscovery.ResolvedTarget
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

/** INTERNAL API */
@InternalApi
private[akka] object BootstrapCoordinator {

  def props(discovery: SimpleServiceDiscovery, joinDecider: JoinDecider, settings: ClusterBootstrapSettings): Props =
    Props(new BootstrapCoordinator(discovery, joinDecider, settings))

  object Protocol {
    final case object InitiateBootstrapping
    sealed trait BootstrappingCompleted
    final case object BootstrappingCompleted extends BootstrappingCompleted

    final case class ObtainedHttpSeedNodesObservation(
        observedAt: LocalDateTime,
        contactPoint: ResolvedTarget,
        seedNodesSourceAddress: Address,
        observedSeedNodes: Set[Address]
    ) extends DeadLetterSuppression

    final case class ProbingFailed(contactPoint: ResolvedTarget, cause: Throwable) extends DeadLetterSuppression
  }

  private case object ResolveTick extends DeadLetterSuppression
  private case object DecideTick extends DeadLetterSuppression

  protected[bootstrap] final case class DnsServiceContactsObservation(observedAt: LocalDateTime,
                                                                      observedContactPoints: Set[ResolvedTarget]) {

    def membersChanged(other: DnsServiceContactsObservation): Boolean =
      this.observedContactPoints != other.observedContactPoints

    def sameOrChanged(other: DnsServiceContactsObservation): DnsServiceContactsObservation =
      if (membersChanged(other)) other
      else this
  }

}

/**
 * Looks up members of the same "service" in DNS and initiates [[HttpContactPointBootstrap]]'s for each such node.
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
 * FIXME explain the races
 */
// also known as the "Baron von Bootstrappen"
/** INTERNAL API */
@InternalApi
private[akka] final class BootstrapCoordinator(discovery: SimpleServiceDiscovery,
                                               joinDecider: JoinDecider,
                                               settings: ClusterBootstrapSettings)
    extends Actor
    with ActorLogging
    with Timers {

  import BootstrapCoordinator.Protocol._
  import BootstrapCoordinator._
  import context.dispatcher

  private val cluster = Cluster(context.system)

  private val ResolveTimerKey = "resolve-key"
  private val DecideTimerKey = "decide-key"

  private var serviceName = settings.contactPointDiscovery.effectiveName(context.system)

  private var lastContactsObservation: Option[DnsServiceContactsObservation] = None
  private var seedNodesObservations: Map[ResolvedTarget, SeedNodesObservation] = Map.empty

  private var decisionInProgress = false

  timers.startPeriodicTimer(ResolveTimerKey, ResolveTick, settings.contactPointDiscovery.interval)
  timers.startPeriodicTimer(DecideTimerKey, DecideTick, settings.contactPoint.probeInterval)

  /** Awaiting initial signal to start the bootstrap process */
  override def receive: Receive = {
    case InitiateBootstrapping ⇒
      log.info("Locating service members; Lookup [{}]. Using discovery [{}], join decider [{}]", serviceName,
        discovery.getClass.getName, joinDecider.getClass.getName)
      lookup()

      context become bootstrapping(sender())
  }

  /** In process of searching for seed-nodes */
  def bootstrapping(replyTo: ActorRef): Receive = {
    case ResolveTick ⇒
      lookup()

    case SimpleServiceDiscovery.Resolved(name, contactPoints) ⇒
      serviceName = name
      onContactPointsResolved(contactPoints)

    case ex: Failure ⇒
      log.warning("Resolve attempt failed! Cause: {}", ex.cause)
      // prevent join decision until successful lookup
      lastContactsObservation = None

    case ObtainedHttpSeedNodesObservation(observedAt, contactPoint, infoFromAddress, observedSeedNodes) ⇒
      lastContactsObservation.foreach { contacts =>
        if (contacts.observedContactPoints.contains(contactPoint)) {
          log.info("Contact point [{}] returned [{}] seed-nodes [{}]", infoFromAddress, observedSeedNodes.size,
            observedSeedNodes.mkString(", "))

          seedNodesObservations = seedNodesObservations.updated(contactPoint,
            new SeedNodesObservation(observedAt, contactPoint, infoFromAddress, observedSeedNodes))
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
            log.info("Joining [{}] to existing cluster [{}]", cluster.selfAddress, seedNodes.mkString(", "))

            val seedNodesList = (seedNodes - cluster.selfAddress).toList // order doesn't matter
            cluster.joinSeedNodes(seedNodesList)

            // once we issued a join bootstrapping is completed
            replyTo ! BootstrappingCompleted
            context.stop(self)
          }
        case JoinSelf =>
          log.info(
              "Initiating new cluster, self-joining [{}]. " +
              "Other nodes are expected to locate this cluster via continued contact-point probing.",
              cluster.selfAddress)

          cluster.join(cluster.selfAddress)

          // once we issued a join bootstrapping is completed
          replyTo ! BootstrappingCompleted
          context.stop(self)
      }

    case ProbingFailed(contactPoint, _) =>
      lastContactsObservation.foreach { contacts =>
        if (contacts.observedContactPoints.contains(contactPoint)) {
          log.info("Received signal that probing has failed, scheduling contact point re-discovery")
        }
      }

      // remove the previous observation since it might be obsolete
      seedNodesObservations -= contactPoint
  }

  private def lookup(): Unit =
    discovery.lookup(serviceName, settings.contactPointDiscovery.resolveTimeout).pipeTo(self)

  private def onContactPointsResolved(contactPoints: immutable.Seq[ResolvedTarget]): Unit = {
    val newObservation = DnsServiceContactsObservation(timeNow(), contactPoints.toSet)
    lastContactsObservation match {
      case Some(contacts) => lastContactsObservation = Some(contacts.sameOrChanged(newObservation))
      case None => lastContactsObservation = Some(newObservation)
    }

    // remove observations from contact points that are not included any more
    seedNodesObservations = seedNodesObservations.filterNot {
      case (contactPoint, _) => !newObservation.observedContactPoints.contains(contactPoint)
    }

    // TODO stop the obsolete children (they are stopped when probing fails for too long)

    newObservation.observedContactPoints.foreach(ensureProbing)
  }

  private def ensureProbing(contactPoint: ResolvedTarget): Option[ActorRef] = {
    val targetPort = contactPoint.port.getOrElse(settings.contactPoint.fallbackPort)
    val rawBaseUri = Uri("http", Uri.Authority(Uri.Host(contactPoint.host), targetPort))
    val baseUri = settings.managementBasePath.fold(rawBaseUri)(prefix => rawBaseUri.withPath(Uri.Path(s"/$prefix")))

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
        def isObsolte(obs: SeedNodesObservation): Boolean =
          Duration.between(obs.observedAt, currentTime).toMillis > settings.contactPoint.probingFailureTimeout.toMillis

        val seedObservations = seedNodesObservations.valuesIterator.filterNot(isObsolte).toSet
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
