/*
 * Copyright (C) 2017-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.coordination.lease.kubernetes

import akka.annotation.InternalApi

import scala.concurrent.Future

/**
 * INTERNAL API
 */
@InternalApi
private[akka] case class LeaseResource(owner: Option[String], version: String, time: Long) {
  def isTaken(): Boolean = owner.isDefined
}

/**
 * INTERNAL API
 */
private[akka] trait KubernetesApi {

  /**
   * Reads a Lease from the API server. If it doesn't exist it tries to create it.
   * The creation can fail due to another instance creating at the same time, in this case
   * the read is retried.
   */
  def readOrCreateLeaseResource(name: String): Future[LeaseResource]

  /**
   * Update the named resource.
   *
   * Must [[readOrCreateLeaseResource]] to first to get a resource version.
   *
   * Can return one of three things:
   *  - Future failure e.g. timed out waiting for k8s api server to respond
   *  - Left - Update failed due to version not matching current in the k8s api server. In this case resource is returned so the version can be used for subsequent calls
   *  - Right - Success
   *
   *  Any subsequent updates should also use the latest version or re-read with [[readOrCreateLeaseResource]]
   */
  def updateLeaseResource(
      name: String,
      clientName: String,
      version: String,
      time: Long = System.currentTimeMillis()): Future[Either[LeaseResource, LeaseResource]]
}
