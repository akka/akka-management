package akka.management.cluster.bootstrap

import akka.actor.ActorSystem
import akka.discovery.MockDiscovery
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.{Matchers, WordSpec}

class DnsSrvBootstrapIntegrationSpec extends WordSpec with Matchers {

  def config(extras: String): Config =
    ConfigFactory.parseString(extras)
      ConfigFactory.parseString(
        s"""
         akka.cluster.bootstrap.contact-point-discovery.method = mock-dns
        """.stripMargin)
    .withFallback(ConfigFactory.load())

  "Forming a cluster using DNS-SRV and HTTP Contact Points" should {
    "work" in {
      val systemA = ActorSystem("A")
      val systemB = ActorSystem("B")
      val systemC = ActorSystem("C")
    }
  }

}
