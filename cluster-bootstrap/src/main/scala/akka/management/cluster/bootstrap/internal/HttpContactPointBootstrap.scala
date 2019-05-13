/*
 * Copyright (C) 2017-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.management.cluster.bootstrap.internal

import java.util.concurrent.TimeoutException

import scala.concurrent.Future
import scala.concurrent.duration._
import akka.actor.Props
import akka.annotation.InternalApi
import akka.cluster.Cluster
import akka.discovery.ServiceDiscovery.ResolvedTarget
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.settings.ConnectionPoolSettings
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.management.cluster.bootstrap.ClusterBootstrapSettings
import akka.management.cluster.bootstrap.contactpoint.{ ClusterBootstrapRequests, HttpBootstrapJsonProtocol }
import akka.pattern.after
import akka.pattern.pipe
import akka.stream.ActorMaterializer
import akka.util.Timeout

@InternalApi
private[bootstrap] object HttpContactPointBootstrap {

  def props(settings: ClusterBootstrapSettings, contactPoint: ResolvedTarget): Props =
    Props(new HttpContactPointBootstrap(settings, contactPoint))
}

/**
 * Intended to be spawned as child actor by a higher-level Bootstrap coordinator that manages obtaining of the URIs.
 *
 * This additional step may at-first seem superficial -- after all, we already have some addresses of the nodes
 * that we'll want to join -- however it is not optional. By communicating with the actual nodes before joining their
 * cluster we're able to inquire about their status, double-check if perhaps they are part of an existing cluster already
 * that we should join, or even coordinate rolling upgrades or more advanced patterns.
 */
@InternalApi
private[bootstrap] class HttpContactPointBootstrap(
    settings: ClusterBootstrapSettings,
    contactPoint: ResolvedTarget
) extends AbstractContactPointBootstrap(settings, contactPoint) with HttpBootstrapJsonProtocol {

  import HttpBootstrapJsonProtocol._

  private val cluster = Cluster(context.system)

  private implicit val mat = ActorMaterializer()(context.system)
  private val http = Http()(context.system)
  private val connectionPoolWithoutRetries = ConnectionPoolSettings(context.system).withMaxRetries(0)
  import context.dispatcher

  private val probeRequest = {
    val targetPort = contactPoint.port.getOrElse(settings.contactPoint.fallbackPort)
    val rawBaseUri = Uri("http", Uri.Authority(Uri.Host(contactPoint.host), targetPort))
    val baseUri = settings.managementBasePath.fold(rawBaseUri)(prefix => rawBaseUri.withPath(Uri.Path(s"/$prefix")))

    if (baseUri.authority.host.address() == cluster.selfAddress.host.getOrElse("---") &&
      baseUri.authority.port == cluster.selfAddress.port.getOrElse(-1)) {
      throw new IllegalArgumentException(
        "Requested base Uri to be probed matches local remoting address, bailing out! " +
          s"Uri: $baseUri, this node's remoting address: ${cluster.selfAddress}")
    }

    ClusterBootstrapRequests.bootstrapSeedNodes(baseUri)
  }
  private val replyTimeout = Future.failed(new TimeoutException(s"Probing timeout of [${probeRequest.uri}]"))

  override val uri: String = probeRequest.uri.toString

  override protected def probe()(implicit probingFailureTimeout: Timeout): Future[SeedNodes] = {
    val reply = http.singleRequest(probeRequest, settings = connectionPoolWithoutRetries).flatMap(handleResponse)

    val afterTimeout = after(settings.contactPoint.probingFailureTimeout, context.system.scheduler)(replyTimeout)
    Future.firstCompletedOf(List(reply, afterTimeout)).pipeTo(self)
  }

  private def handleResponse(response: HttpResponse): Future[SeedNodes] = {
    val strictEntity = response.entity.toStrict(1.second)

    if (response.status == StatusCodes.OK)
      strictEntity.flatMap(res ⇒ Unmarshal(res).to[SeedNodes])
    else
      strictEntity.flatMap { entity =>
        val body = entity.data.utf8String
        Future.failed(
            new IllegalStateException(s"Expected response '200 OK' but found ${response.status}. Body: '$body'"))
      }
  }
}
