/*
 * Copyright (C) 2017 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.discovery.dns

import java.net.InetAddress

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

/**
 * Looks for A records for a given service.
 */
class DnsSimpleServiceDiscovery(system: ActorSystem) extends SimpleServiceDiscovery {

  // Required for async dns, 15 for additional records
  AkkaVersion.require("discovery-dns", "2.5.14")
  require(system.settings.config.getString("akka.io.dns.resolver") == "async-dns",
    "Akka discovery DNS requires akka.io.dns.resolver to be set to async-dns")

  import SimpleServiceDiscovery._

  private val log = Logging(system, getClass)
  private val dns = IO(Dns)(system)

  import system.dispatcher

  private def cleanIpString(ipString: String): String =
    if (ipString.startsWith("/")) ipString.tail else ipString

  override def lookup(lookup: Lookup, resolveTimeout: FiniteDuration): Future[Resolved] = {
    lookup match {
      case Lookup(name, Some(portName), Some(protocol)) =>
        val srvRequest = s"_$portName._$protocol.$name"
        log.debug("Lookup [{}] translated to SRV query [{}] as contains portName and protocol", lookup, srvRequest)
        dns.ask(DnsProtocol.Resolve(srvRequest, Srv))(resolveTimeout).map {
          case resolved: DnsProtocol.Resolved =>
            log.debug("Resolved Dns.Resolved: {}", resolved)
            val ips: Map[String, InetAddress] = resolved.additionalRecords.collect {
              case a: ARecord => a.name -> a.ip
              case aaaa: AAAARecord => aaaa.name -> aaaa.ip
            }.toMap

            val addresses = resolved.records.collect {
              case srv: SRVRecord => ResolvedTarget(srv.target, Some(srv.port), ips.get(srv.target))
            }
            Resolved(srvRequest, addresses)
          case resolved ⇒
            log.warning("Resolved UNEXPECTED (resolving to Nil): {}", resolved.getClass)
            Resolved(srvRequest, Nil)
        }
      case _ =>
        log.debug("Lookup[{}] translated to A/AAAA lookup as does not have portName and protocol", lookup)
        dns.ask(DnsProtocol.Resolve(lookup.serviceName, Ip()))(resolveTimeout).map {
          case resolved: DnsProtocol.Resolved =>
            log.debug("Resolved Dns.Resolved: {}", resolved)
            val addresses = resolved.records.collect {
              case a: ARecord => ResolvedTarget(cleanIpString(a.ip.getHostAddress), None)
              case a: AAAARecord => ResolvedTarget(cleanIpString(a.ip.getHostAddress), None)
            }
            Resolved(lookup.serviceName, addresses)
          case resolved ⇒
            log.warning("Resolved UNEXPECTED (resolving to Nil): {}", resolved.getClass)
            Resolved(lookup.serviceName, Nil)

        }
    }
  }
}
