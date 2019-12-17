/*
 * Copyright (C) 2017-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.management.cluster.bootstrap

import java.time.Duration

import scala.concurrent.Future
import akka.actor.ActorSystem
import akka.actor.Address
import akka.discovery.ServiceDiscovery.ResolvedTarget

/**
 * The decision of joining "self" is made by deterministically sorting the discovered services
 * and picking the *lowest* address. Only the node with lowest address joins itself.
 *
 * If any of the contact-points returns a list of seed nodes it joins the existing cluster immediately.
 *
 * Joining "self" is only done when enough number of contact points have been discovered (`required-contact-point-nr`)
 * and there have been no changes to the discovered contact points during the `stable-margin`.
 *
 * There must also be seed node observations from all discovered contact points before joining "self".
 */
class LowestAddressJoinDecider(system: ActorSystem, settings: ClusterBootstrapSettings)
    extends SelfAwareJoinDecider(system, settings) {

  override def decide(info: SeedNodesInformation): Future[JoinDecision] =
    if (info.hasSeedNodes) {
      val seeds = joinOtherSeedNodes(info)
      if (seeds.isEmpty) KeepProbing.asCompletedFuture else JoinOtherSeedNodes(seeds).asCompletedFuture
    } else if (!hasEnoughContactPoints(info)) {
      log.info(
        BootstrapLogMarker.inProgress(info.contactPoints.map(contactPointString), info.allSeedNodes),
        "Discovered [{}] contact points, confirmed [{}], which is less than the required [{}], retrying",
        info.contactPoints.size, info.seedNodesObservations.size,
        settings.contactPointDiscovery.requiredContactPointsNr)
      KeepProbing.asCompletedFuture
    } else if (!isPastStableMargin(info)) {
      log.debug(
        BootstrapLogMarker.inProgress(info.contactPoints.map(contactPointString), info.allSeedNodes),
        "Contact points observations have changed more recently than the stable-margin [{}], changed at [{}], " +
        "not joining myself. This process will be retried.", settings.contactPointDiscovery.stableMargin,
        info.contactPointsChangedAt)
      KeepProbing.asCompletedFuture
    } else {
      // no seed nodes
      val contactPointsWithoutSeedNodesObservations =
        if (isConfirmedCommunicationWithAllContactPointsRequired(info))
          info.contactPoints -- info.seedNodesObservations.map(_.contactPoint)
        else
          Set.empty[ResolvedTarget]
      if (contactPointsWithoutSeedNodesObservations.isEmpty) {
        // got info from all contact points as expected
        val lowestAddress = lowestAddressContactPoint(info)
        // can the lowest address, if exists, join self
        val isJoinSelfAble = lowestAddress.exists(canJoinSelf(_, info))

        if (isJoinSelfAble && settings.newClusterEnabled)
          JoinSelf.asCompletedFuture
        else {
          if (log.isInfoEnabled) {
            if (settings.newClusterEnabled)
              log.info(
                BootstrapLogMarker.inProgress(info.contactPoints.map(contactPointString), info.allSeedNodes),
                "Exceeded stable margins without locating seed-nodes, however this node {} is NOT the lowest address " +
                "out of the discovered endpoints in this deployment, thus NOT joining self. Expecting node [{}] " +
                "(out of [{}]) to perform the self-join and initiate the cluster.",
                contactPointString(selfContactPoint),
                lowestAddress.map(contactPointString).getOrElse(""),
                info.contactPoints.map(contactPointString).mkString(", "))
            else
              log.warning(
                BootstrapLogMarker.inProgress(info.contactPoints.map(contactPointString), info.allSeedNodes),
                "Exceeded stable margins without locating seed-nodes, however this node {} is configured with " +
                "new-cluster-enabled=off, thus NOT joining self. Expecting existing cluster or node [{}] " +
                "(out of [{}]) to perform the self-join and initiate the cluster.",
                contactPointString(selfContactPoint),
                lowestAddress.map(contactPointString).getOrElse(""),
                info.contactPoints.map(contactPointString).mkString(", "))
          }

          // the probing will continue until the lowest addressed node decides to join itself.
          // note, that due to DNS changes this may still become this node! We'll then await until the dns stableMargin
          // is exceeded and would decide to try joining self again (same code-path), that time successfully though.
          KeepProbing.asCompletedFuture
        }
      } else {
        // missing info from some contact points (e.g. because of probe failing)
        if (log.isInfoEnabled)
          log.info(
            BootstrapLogMarker.inProgress(info.contactPoints.map(contactPointString), info.allSeedNodes),
            "Exceeded stable margins but missing seed node information from some contact points [{}] (out of [{}])",
            contactPointsWithoutSeedNodesObservations.map(contactPointString).mkString(", "),
            info.contactPoints.map(contactPointString).mkString(", "))

        KeepProbing.asCompletedFuture
      }

    }

  /**
   * May be overridden by subclass to extract the nodes to use as seed nodes when joining
   * existing cluster. `info.allSeedNodes` contains all existing nodes.
   * If the returned `Set` is empty it will continue probing.
   */
  protected def joinOtherSeedNodes(info: SeedNodesInformation): Set[Address] =
    info.allSeedNodes.take(5)

  /**
   * May be overridden by subclass to decide if enough contact points have been discovered.
   * `info.contactPoints.size` is the number of discovered (e.g. via DNS lookup) contact points
   * and `info.seedNodesObservations.size` is the number that has been confirmed that they are
   * reachable and running.
   */
  protected def hasEnoughContactPoints(info: SeedNodesInformation): Boolean =
    info.seedNodesObservations.size >= settings.contactPointDiscovery.requiredContactPointsNr

  /**
   * May be overridden by subclass to decide if the set of discovered contact points is stable.
   * `info.contactPointsChangedAt` was the time when the discovered contact points were changed
   * last time. Subsequent lookup attempts after that returned the same contact points.
   */
  protected def isPastStableMargin(info: SeedNodesInformation): Boolean = {
    val contactPointsChanged = Duration.between(info.contactPointsChangedAt, info.currentTime)
    contactPointsChanged.toMillis >= settings.contactPointDiscovery.stableMargin.toMillis
  }

  /**
   * May be overridden by subclass to allow joining self even though some of the discovered
   * contact points have not been confirmed (unreachable or not running).
   * `hasEnoughContactPoints` and `isPastStableMargin` must still be fulfilled.
   */
  protected def isConfirmedCommunicationWithAllContactPointsRequired(info: SeedNodesInformation): Boolean =
    true

  /**
   * Contact point with the "lowest" contact point address,
   * it is expected to join itself if no other cluster is found in the deployment.
   *
   * May be overridden by subclass for example if another sort order is desired.
   */
  protected def lowestAddressContactPoint(info: SeedNodesInformation): Option[ResolvedTarget] = {
    // Note that we are using info.seedNodesObservations and not info.contactPoints here, but that
    // is the same when isConfirmedCommunicationWithAllContactPointsRequired == true
    info.seedNodesObservations.toList.map(_.contactPoint).sorted.headOption
  }
}
