/*
 * Copyright (C) 2017 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.discovery.dns

import akka.AkkaVersion
import akka.actor.ActorSystem
import akka.event.Logging
import akka.io.{Dns, IO}
import akka.pattern.ask

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import akka.discovery._
import akka.io.dns.{AAAARecord, ARecord, DnsProtocol, SRVRecord}
import akka.io.dns.DnsProtocol.{Ip, Srv}



/**
  * Looks for A records for a given service.
  */
class DnsSimpleServiceDiscovery(system: ActorSystem) extends SimpleServiceDiscovery {

  // FIXME once 2.5.12 is released
  // Required for async dns
  // AkkaVersion.require("discovery-dns", "2.5.14")
  require(system.settings.config.getString("akka.io.dns.resolver") == "async-dns",
    "Akka discovery DNS requires akka.io.dns.resolver to be set to async-dns")

  import SimpleServiceDiscovery._

  private val log = Logging(system, getClass)
  private val dns = IO(Dns)(system)

  import system.dispatcher

  override def lookup(query: Lookup, resolveTimeout: FiniteDuration): Future[Resolved] = {
    def cleanIpString(ipString: String): String =
      if (ipString.startsWith("/")) ipString.tail else ipString


    query match {
      case Simple(name) =>
        log.debug("Simple query [{}] translated to A/AAAA lookup", query)
        dns.ask(DnsProtocol.Resolve(name, Ip()))(resolveTimeout).map {
          case resolved: DnsProtocol.Resolved =>
            log.debug("Resolved Dns.Resolved: {}", resolved)
            val addresses = resolved.results.collect {
              case a: ARecord => ResolvedTarget(cleanIpString(a.ip.getHostAddress), None)
              case a: AAAARecord => ResolvedTarget(cleanIpString(a.ip.getHostAddress), None)
            }
            Resolved(query.name, addresses)

          case resolved ⇒
            log.warning("Resolved UNEXPECTED (resolving to Nil): {}", resolved.getClass)
            Resolved(query.name, Nil)
        }

      case Full(name, port, protocol) =>
        val srvRequest = s"_$port._$protocol.$name"
        log.debug("Full query [{}] translated to srv query [{}]", query, srvRequest)
        dns.ask(DnsProtocol.Resolve(srvRequest, Srv))(resolveTimeout).map {
          case resolved: DnsProtocol.Resolved =>
            log.debug("Resolved Dns.Resolved: {}", resolved)
            val addresses = resolved.results.collect {
              case srv: SRVRecord => ResolvedTarget(srv.target, Some(srv.port))
            }
            Resolved(srvRequest, addresses)

          case resolved ⇒
            log.warning("Resolved UNEXPECTED (resolving to Nil): {}", resolved.getClass)
            Resolved(srvRequest, Nil)
        }
    }
  }
}
