/*
 * Copyright (C) 2017 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.cluster.bootstrap.discovery

import akka.actor.ActorSystem
import akka.discovery.SimpleServiceDiscovery.ResolvedTarget
import akka.discovery.consul.ConsulSimpleServiceDiscovery
import akka.testkit.TestKitBase
import com.google.common.net.HostAndPort
import com.orbitz.consul.Consul
import com.orbitz.consul.model.catalog.{CatalogRegistration, ImmutableCatalogRegistration}
import com.orbitz.consul.model.health.{ImmutableService, Service}
import com.pszymczyk.consul.{ConsulProcess, ConsulStarterBuilder}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.duration._

class ConsulDiscoverySpec extends WordSpecLike with Matchers with BeforeAndAfterAll with TestKitBase with ScalaFutures {

  private var consul: ConsulProcess = null

  "Consul Discovery" should {
    "work" in {
      val consulAgent = Consul.builder().withHostAndPort(HostAndPort.fromParts(consul.getAddress, consul.getHttpPort)).build()
      consulAgent.catalogClient().register(
        ImmutableCatalogRegistration.builder().service(
          ImmutableService.builder()
            .addTags(s"system:${system.name}", "akka-management-port:1234")
            .address("127.0.0.1")
            .id("test")
            .service("test")
            .port(1235)
            .build()
        )
          .node("testNode")
          .address("localhost")
          .build()
      )

      val lookupService = new ConsulSimpleServiceDiscovery(system)
      val resolved = lookupService.lookup("test", 10 seconds).futureValue
      resolved.addresses should contain(ResolvedTarget("127.0.0.1", Some(1234)))
    }
  }

  override def beforeAll(): Unit = {
    super.beforeAll()
    consul = ConsulStarterBuilder.consulStarter().withHttpPort(8500).build().start()
  }

  override def afterAll(): Unit = {
    super.afterAll()
    consul.close()
  }

  override implicit lazy val system: ActorSystem = ActorSystem("test")
}
