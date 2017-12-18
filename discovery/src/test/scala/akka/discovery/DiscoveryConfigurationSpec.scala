/*
 * Copyright (C) 2017 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.discovery

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import org.scalatest.{ Matchers, WordSpec }

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

class DiscoveryConfigurationSpec extends WordSpec with Matchers {

  "ServiceDiscovery" should {
    "throw when no default discovery configured" in {
      val sys = ActorSystem()

      val ex = intercept[Exception] {
        ServiceDiscovery(sys).discovery
      }
      ex.getMessage should include("No default service discovery implementation configured")
    }

    "select implementation from config" in {
      val className = classOf[FakeTestDiscovery].getCanonicalName

      val sys = ActorSystem("example", ConfigFactory.parseString(s"""
          akka.discovery.method = $className
        """.stripMargin).withFallback(ConfigFactory.load()))

      ServiceDiscovery(sys).discovery.getClass.getCanonicalName should ===(className)
    }
  }

}

class FakeTestDiscovery extends SimpleServiceDiscovery {
  override def lookup(name: String, resolveTimeout: FiniteDuration): Future[SimpleServiceDiscovery.Resolved] =
    ???
}
