/*
 * Copyright (C) 2017 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.discovery.marathon

import scala.collection.immutable
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

import akka.actor.ActorSystem
import akka.discovery.SimpleServiceDiscovery.Resolved
import akka.discovery.{ Lookup, SimpleServiceDiscovery }
import akka.http.scaladsl.{ Http, HttpExt }

/**
 * Service discovery that uses the Marathon API.
 *
 * A target address is resolved when the app/pod has the expected label,
 * there is a configured akka management port/endpoint and the target
 * address is in the expected subnet (if specified).
 *
 * @param system actor system
 */
class MarathonApiSimpleServiceDiscovery(system: ActorSystem) extends SimpleServiceDiscovery {

  import system.dispatcher

  private implicit val sys: ActorSystem = implicitly(system)
  private implicit val http: HttpExt = Http()(system)
  private implicit val settings: Settings = Settings(system)

  private val appDiscovery = new ServiceDiscovery.ForApps(settings)
  private val podDiscovery = new ServiceDiscovery.ForPods(settings)

  override def lookup(
      lookup: Lookup,
      resolveTimeout: FiniteDuration
  ): Future[Resolved] = {
    implicit val timeout: FiniteDuration = implicitly(resolveTimeout)

    val appTargets = appDiscovery.resolveTargets(lookup.serviceName)
    val podTargets = podDiscovery.resolveTargets(lookup.serviceName)

    for {
      apps <- appTargets
      pods <- podTargets
    } yield {
      Resolved(
        lookup.serviceName,
        addresses = (apps ++ pods).to[immutable.Seq]
      )
    }
  }
}
