/*
 * Copyright (C) 2017-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.rollingupdate.kubernetes

import java.text.Normalizer

import scala.collection.immutable
import scala.concurrent.Future

import akka.Done
import akka.actor.AddressFromURIString
import akka.annotation.InternalApi
import akka.cluster.UniqueAddress

/**
 * INTERNAL API
 */
@InternalApi
private[akka] final case class PodCostResource(version: String, pods: immutable.Seq[PodCost])

/**
 * INTERNAL API
 */
@InternalApi
private[akka] final case class PodCost(podName: String, cost: Int, address: String, uid: Long, time: Long) {
  @transient
  lazy val uniqueAddress: UniqueAddress = UniqueAddress(AddressFromURIString(address), uid)
}

/**
 * INTERNAL API
 */
@InternalApi private[akka] sealed class PodCostException(message: String) extends RuntimeException(message)

/**
 * INTERNAL API
 */
@InternalApi private[akka] final class PodCostTimeoutException(message: String) extends PodCostException(message)

/**
 * INTERNAL API
 */
@InternalApi private[akka] final class PodCostClientException(message: String) extends PodCostException(message)

/**
 * INTERNAL API
 */
@InternalApi private[akka] object KubernetesApi {

  /**
   * Limit the length of a name to 63 characters.
   * Some subsystem of Kubernetes cannot manage longer names.
   */
  private def truncateTo63Characters(name: String): String =
    // FIXME fail instead, see issue https://github.com/akka/akka-management/issues/910
    name.take(63)

  /**
   * Removes from the leading and trailing positions the specified characters.
   */
  private def trim(name: String, characters: List[Char]): String =
    name.dropWhile(characters.contains(_)).reverse.dropWhile(characters.contains(_)).reverse

  /**
   * Make a name compatible with DNS 1039 standard: like a single domain name segment.
   * Regex to follow: [a-z]([-a-z0-9]*[a-z0-9])
   * Limit the resulting name to 63 characters
   */
  def makeDNS1039Compatible(name: String): String = {
    val normalized =
      Normalizer.normalize(name, Normalizer.Form.NFKD).toLowerCase.replaceAll("[_.]", "-").replaceAll("[^-a-z0-9]", "")
    trim(truncateTo63Characters(normalized), List('-'))
  }
}

/**
 * INTERNAL API
 */
private[akka] trait KubernetesApi {

  def namespace: String

  def updatePodDeletionCostAnnotation(podName: String, cost: Int): Future[Done]

  /**
   * Reads a PodCost from the API server. If it doesn't exist it tries to create it.
   * The creation can fail due to another instance creating at the same time, in this case
   * the read is retried.
   */
  def readOrCreatePodCostResource(crName: String): Future[PodCostResource]

  /**
   * Update the named resource.
   *
   * Must [[readOrCreatePodCostResource]] to first to get a resource version.
   *
   * Can return one of three things:
   *  - Future failure e.g. timed out waiting for k8s api server to respond
   *  - Left - Update failed due to version not matching current in the k8s api server. In this case resource is returned so the version can be used for subsequent calls
   *  - Right - Success
   *
   *  Any subsequent updates should also use the latest version or re-read with [[readOrCreatePodCostResource]]
   */
  def updatePodCostResource(
      crName: String,
      version: String,
      pods: immutable.Seq[PodCost]): Future[Either[PodCostResource, PodCostResource]]

}
