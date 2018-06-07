/*
 * Copyright (C) 2017 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.discovery.dns

import akka.AkkaVersion
import akka.actor.ActorSystem
import akka.event.Logging
import akka.io.{ Dns, IO }
import akka.pattern.ask

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import akka.discovery._
import akka.io.dns.{ AAAARecord, ARecord, DnsProtocol, SRVRecord }
import akka.io.dns.DnsProtocol.{ Ip, Srv }
import com.typesafe.config.Config

class DnsDiscoverySettings(config: Config) {
  val LookupType = config.getString("akka.discovery.akka-dns.lookup-type").toLowerCase() match {
    case "srv" => Srv
    case "ip" => Ip()
  }
}

/**
 * Looks for A records for a given service.
 */
class DnsSimpleServiceDiscovery(system: ActorSystem) extends SimpleServiceDiscovery {

  // Required for async dns
  // AkkaVersion.require("discovery-dns", "2.5.14")
  require(system.settings.config.getString("akka.io.dns.resolver") == "async-dns",
    "Akka discovery DNS requires akka.io.dns.resolver to be set to async-dns")

  import SimpleServiceDiscovery._

  private val log = Logging(system, getClass)
  private val dns = IO(Dns)(system)
  private val settings = new DnsDiscoverySettings(system.settings.config)

  import system.dispatcher

  override def lookup(query: Lookup, resolveTimeout: FiniteDuration): Future[Resolved] = {
    def cleanIpString(ipString: String): String =
      if (ipString.startsWith("/")) ipString.tail else ipString

    log.debug("Resolving {} type {}", name, settings.LookupType)

    dns.ask(DnsProtocol.Resolve(query.name, settings.LookupType))(resolveTimeout) map {
      case resolved: DnsProtocol.Resolved =>
        log.debug("Resolved Dns.Resolved: {}", resolved)
        val addresses = resolved.results.collect {
          case a: ARecord => ResolvedTarget(cleanIpString(a.ip.getHostAddress), None)
          case a: AAAARecord => ResolvedTarget(cleanIpString(a.ip.getHostAddress), None)
          case srv: SRVRecord => ResolvedTarget(srv.target, Some(srv.port))
        }
        Resolved(query.name, addresses)

      case resolved ⇒
        log.warning("Resolved UNEXPECTED (resolving to Nil): {}", resolved.getClass)
        Resolved(query.name, Nil)
    }
  }
}
