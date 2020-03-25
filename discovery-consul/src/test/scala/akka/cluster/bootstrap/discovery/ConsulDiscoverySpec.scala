/*
 * Copyright (C) 2017-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.cluster.bootstrap.discovery

import java.net.InetAddress

import akka.actor.ActorSystem
import akka.discovery.ServiceDiscovery.ResolvedTarget
import akka.discovery.consul.ConsulServiceDiscovery
import akka.testkit.TestKitBase
import com.google.common.net.HostAndPort
import com.orbitz.consul.Consul
import com.pszymczyk.consul.{ ConsulProcess, ConsulStarterBuilder }
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{ Millis, Seconds, Span }
import org.scalatest.{ BeforeAndAfterAll, Matchers, WordSpecLike }

import scala.collection.JavaConverters._
import scala.concurrent.duration._

class ConsulDiscoverySpec extends WordSpecLike with Matchers with BeforeAndAfterAll with TestKitBase with ScalaFutures {

  private val consul: ConsulProcess =
    ConsulStarterBuilder.consulStarter().withHttpPort(8500).build().start()

  "Consul Discovery" should {
    "work for defaults" in {
      val consulAgent =
        Consul.builder().withHostAndPort(HostAndPort.fromParts(consul.getAddress, consul.getHttpPort)).build()
      consulAgent
        .agentClient()
        .register(
          8558,
          HostAndPort.fromParts("127.0.0.1", 8558),
          10,
          "test-akka-management",
          "test-akka-management-8558",
          Seq.empty[String].asJava,
          Map.empty[String, String].asJava
        )

      val lookupService = new ConsulServiceDiscovery(system)
      val resolved = lookupService.lookup("test", 10.seconds).futureValue
      resolved.addresses should contain(
        ResolvedTarget(host = "127.0.0.1", port = Some(8558), address = Some(InetAddress.getByName("127.0.0.1")))
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
