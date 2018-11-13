/*
 * Copyright (C) 2017 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.cluster.bootstrap.discovery

import java.net.InetAddress

import akka.actor.ActorSystem
import akka.discovery.{ Lookup, ServiceDiscovery }
import akka.discovery.SimpleServiceDiscovery.ResolvedTarget
import akka.event.LoggingAdapter
import akka.testkit.{ SocketUtil, TestKit }
import com.typesafe.config.ConfigFactory
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{ Millis, Seconds, Span }
import org.scalatest.{ BeforeAndAfterAll, Matchers, WordSpecLike }

import scala.concurrent.duration._

object DnsDiscoverySpec {

  val config = ConfigFactory.parseString(s"""
     //#configure-dns
     akka {
       discovery {
        method = akka-dns
       }
     }
     //#configure-dns
     akka {
       loglevel = DEBUG
     }
      akka.io.dns.async-dns.nameservers = ["localhost:${DnsDiscoverySpec.dockerDnsServerPort}"]
    """)

  lazy val dockerDnsServerPort = SocketUtil.temporaryLocalPort()

  val configWithAsyncDnsResolverAsDefault = ConfigFactory.parseString("""
      akka.io.dns.resolver = "async-dns"
    """).withFallback(config)

}

class DnsDiscoverySpec
    extends TestKit(ActorSystem("DnsDiscoverySpec", DnsDiscoverySpec.config))
    with WordSpecLike
    with Matchers
    with BeforeAndAfterAll
    with ScalaFutures
    with DockerBindDnsService {

  import DnsDiscoverySpec._

  override val hostPort: Int = DnsDiscoverySpec.dockerDnsServerPort
  override val log: LoggingAdapter = system.log

  val systemWithAsyncDnsAsResolver = ActorSystem("AsyncDnsSystem", configWithAsyncDnsResolverAsDefault)

  implicit val patience: PatienceConfig = PatienceConfig(timeout = Span(11, Seconds), interval = Span(250, Millis))

  "Dns Discovery with isolated resolver" must {

    "work with SRV records" in {
      val discovery = ServiceDiscovery(system).discovery
      val name = "_service._tcp.foo.test."
      val result =
        discovery
          .lookup(Lookup("foo.test.").withPortName("service").withProtocol("tcp"), resolveTimeout = 10.seconds)
          .futureValue
      result.addresses.toSet shouldEqual Set(
        ResolvedTarget("a-single.foo.test", Some(5060), Some(InetAddress.getByName("192.168.1.20"))),
        ResolvedTarget("a-double.foo.test", Some(65535), Some(InetAddress.getByName("192.168.1.21"))),
        ResolvedTarget("a-double.foo.test", Some(65535), Some(InetAddress.getByName("192.168.1.22")))
      )
      result.serviceName shouldEqual name
    }

    "work with IP records" in {
      val discovery = ServiceDiscovery(system).discovery
      val name = "a-single.foo.test"
      val result = discovery.lookup(name, resolveTimeout = 500.milliseconds).futureValue
      result.serviceName shouldEqual name
      result.addresses.toSet shouldEqual Set(
        ResolvedTarget("192.168.1.20", None)
      )
    }

    "be using its own resolver" in {
      // future will fail if it it doesn't exist
      system.actorSelection("/system/SD-DNS/async-dns").resolveOne(2.seconds).futureValue
    }

  }

  "Dns discovery with the system resolver" must {
    "work with SRV records" in {
      val discovery = ServiceDiscovery(systemWithAsyncDnsAsResolver).discovery
      val name = "_service._tcp.foo.test."
      val result =
        discovery
          .lookup(Lookup("foo.test.").withPortName("service").withProtocol("tcp"), resolveTimeout = 10.seconds)
          .futureValue
      result.addresses.toSet shouldEqual Set(
        ResolvedTarget("a-single.foo.test", Some(5060), Some(InetAddress.getByName("192.168.1.20"))),
        ResolvedTarget("a-double.foo.test", Some(65535), Some(InetAddress.getByName("192.168.1.21"))),
        ResolvedTarget("a-double.foo.test", Some(65535), Some(InetAddress.getByName("192.168.1.22")))
      )
      result.serviceName shouldEqual name
    }

    "be using the system resolver" in {
      // check the service discovery one doesn't exist
      systemWithAsyncDnsAsResolver.actorSelection("/system/SD-DNS/async-dns").resolveOne(2.seconds).failed.futureValue
    }

  }

  override def afterAll(): Unit = {
    super.afterAll()
    TestKit.shutdownActorSystem(system)
  }
}
