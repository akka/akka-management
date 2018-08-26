/*
 * Copyright (C) 2017 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.discovery.dns

import java.net.{ Inet6Address, InetAddress }

import akka.discovery.SimpleServiceDiscovery
import akka.discovery.SimpleServiceDiscovery.{ Resolved, ResolvedTarget }
import akka.io.dns.{ AAAARecord, ARecord, DnsProtocol, SRVRecord }
import org.scalatest.{ Matchers, WordSpec }

import scala.collection.{ immutable => im }

class DnsSimpleServiceDiscoverySpec extends WordSpec with Matchers {
  "srvRecordsToResolved" must {
    "fill in ips from A records" in {
      val resolved = DnsProtocol.Resolved("cats.com", im.Seq(new SRVRecord("cats1.com", 1, 2, 3, 4, "kittens.com")),
        im.Seq(new ARecord("kittens.com", 1, InetAddress.getByName("127.0.0.2"))))

      val result: SimpleServiceDiscovery.Resolved =
        DnsSimpleServiceDiscovery.srvRecordsToResolved("cats.com", resolved)

      result shouldEqual Resolved("cats.com",
        List(ResolvedTarget("kittens.com", Some(4), Some(InetAddress.getByName("127.0.0.2")))))

    }

    "fill in ips from AAAA records" in {
      val resolved = DnsProtocol.Resolved("cats.com", im.Seq(new SRVRecord("cats1.com", 1, 2, 3, 4, "kittens.com")),
        im.Seq(new AAAARecord("kittens.com", 2, InetAddress.getByName("::1").asInstanceOf[Inet6Address])))

      val result: SimpleServiceDiscovery.Resolved =
        DnsSimpleServiceDiscovery.srvRecordsToResolved("cats.com", resolved)

      result shouldEqual Resolved("cats.com",
        List(ResolvedTarget("kittens.com", Some(4), Some(InetAddress.getByName("::1")))))

    }
  }
}
