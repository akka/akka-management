/*
 * Copyright (C) 2017-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.cluster.bootstrap.discovery

import java.net.InetAddress

import akka.actor.ActorSystem
import akka.discovery.ServiceDiscovery.ResolvedTarget
import akka.discovery.consul.ConsulServiceDiscovery
import akka.testkit.TestKitBase
import com.google.common.net.HostAndPort
import com.orbitz.consul.Consul
import com.orbitz.consul.model.catalog.ImmutableCatalogRegistration
import com.orbitz.consul.model.health.ImmutableService
import com.pszymczyk.consul.{ ConsulProcess, ConsulStarterBuilder }
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{ Millis, Seconds, Span }
import org.scalatest.BeforeAndAfterAll

import scala.concurrent.duration._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

class ConsulDiscoverySpec extends AnyWordSpecLike with Matchers with BeforeAndAfterAll with TestKitBase with ScalaFutures {

  private val consul: ConsulProcess = ConsulStarterBuilder.consulStarter().withHttpPort(8500).build().start()

  "Consul Discovery" should {
    "work for defaults" in {
      val consulAgent =
        Consul.builder().withHostAndPort(HostAndPort.fromParts(consul.getAddress, consul.getHttpPort)).build()
      consulAgent
        .catalogClient()
        .register(
          ImmutableCatalogRegistration
            .builder()
            .service(
              ImmutableService
                .builder()
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

      val lookupService = new ConsulServiceDiscovery(system)
      val resolved = lookupService.lookup("test", 10.seconds).futureValue
      resolved.addresses should contain(
        ResolvedTarget(
          host = "127.0.0.1",
          port = Some(1234),
          address = Some(InetAddress.getByName("127.0.0.1"))
        )
      )
    }
  }

  override def afterAll(): Unit = {
    super.afterAll()
    consul.close()
  }

  override implicit lazy val system: ActorSystem = ActorSystem("test")

  implicit override val patienceConfig: PatienceConfig =
    PatienceConfig(timeout = scaled(Span(30, Seconds)), interval = scaled(Span(50, Millis)))

}
