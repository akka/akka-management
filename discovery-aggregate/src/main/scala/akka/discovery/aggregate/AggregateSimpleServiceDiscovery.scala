/*
 * Copyright (C) 2017 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.discovery.aggregate

import akka.actor.ExtendedActorSystem
import akka.discovery.SimpleServiceDiscovery.Resolved
import akka.discovery.aggregate.AggregateSimpleServiceDiscovery.Mechanisms
import akka.discovery.{ ServiceDiscovery, SimpleServiceDiscovery }
import akka.event.Logging
import akka.util.Helpers.Requiring
import com.typesafe.config.Config

import scala.collection.JavaConverters._
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

final class AggregateSimpleServiceDiscoverySettings(config: Config) {

  val discoveryMechanisms = config
    .getStringList("discovery-mechanisms")
    .asScala
    .toList
    .requiring(_.nonEmpty, "At least one discovery mechanism should be specified")

}

object AggregateSimpleServiceDiscovery {
  type Mechanisms = List[(String, SimpleServiceDiscovery)]
}

final class AggregateSimpleServiceDiscovery(system: ExtendedActorSystem) extends SimpleServiceDiscovery {

  private val log = Logging(system, getClass)

  private val settings =
    new AggregateSimpleServiceDiscoverySettings(system.settings.config.getConfig("akka.discovery.aggregate"))

  private val mechanisms =
    settings.discoveryMechanisms.map(mech => (mech, ServiceDiscovery.loadServiceDiscovery(mech, system)))
  private implicit val ec = system.dispatcher

  /**
   * Each discovery mechanism is given the resolveTimeout rather than reducing it each time between mechanisms.
   */
  override def lookup(name: String, resolveTimeout: FiniteDuration): Future[SimpleServiceDiscovery.Resolved] = {
    resolve(mechanisms, name, resolveTimeout)
  }

  private def resolve(sds: Mechanisms, name: String, resolveTimeout: FiniteDuration): Future[Resolved] = {
    sds match {
      case (mechanism, next) :: Nil =>
        log.debug("Looking up [{}] with [{}]", name, mechanism)
        next.lookup(name, resolveTimeout)
      case (mechanism, next) :: tail =>
        log.debug("Looking up [{}] with [{}]", name, mechanism)
        // If nothing comes back then try the next one
        next
          .lookup(name, resolveTimeout)
          .flatMap { resolved =>
            if (resolved.addresses.isEmpty) {
              log.debug("Mechanism [{}] returned no ResolvedTargets, trying next", name)
              resolve(tail, name, resolveTimeout)
            } else
              Future.successful(resolved)
          }
          .recoverWith {
            case t: Throwable =>
              log.error(t, "[{}] Service discovery failed. Trying next discovery mechanism", mechanism)
              resolve(tail, name, resolveTimeout)
          }
    }
  }
}
