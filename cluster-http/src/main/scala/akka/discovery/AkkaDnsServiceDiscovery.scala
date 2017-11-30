/*
 * Copyright (C) 2017 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.discovery

import akka.actor.ActorSystem
import akka.event.Logging
import akka.io.{ Dns, IO }
import akka.pattern.ask

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

/**
 * Looks for A records for a given service.
 */
class AkkaDnsServiceDiscovery(system: ActorSystem) extends ServiceDiscovery {
  private val log = Logging(system, getClass)
  private val dns = IO(Dns)(system)
  import system.dispatcher

  override def lookup(name: String, resolveTimeout: FiniteDuration): Future[ServiceDiscovery.Resolved] = {
    def cleanIpString(ipString: String): String =
      if (ipString.startsWith("/")) ipString.tail else ipString

    dns.ask(Dns.Resolve(name))(resolveTimeout) map {
      case resolved: Dns.Resolved =>
        log.info("Resolved Dns.Resolved: {}", resolved)
        val addresses = resolved.ipv4.map { entry ⇒
          ServiceDiscovery.ResolvedTarget(cleanIpString(entry.getHostAddress), None)
        }
        ServiceDiscovery.Resolved(name, addresses)

      case resolved ⇒
        log.warning("Resolved UNEXPECTED (resolving to Nil): {}", resolved.getClass)
        ServiceDiscovery.Resolved(name, Nil)
    }
  }
}
