/*
 * Copyright (C) 2017 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.discovery

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

import akka.actor.ActorSystem
import akka.testkit.TestKit
import com.typesafe.config.ConfigFactory
import org.scalatest.Matchers
import org.scalatest.WordSpec

class DiscoveryConfigurationSpec extends WordSpec with Matchers {

  "ServiceDiscovery" should {
    "throw when no default discovery configured" in {
      val sys = ActorSystem("DiscoveryConfigurationSpec")
      try {
        val ex = intercept[Exception] {
          ServiceDiscovery(sys).discovery
        }
        ex.getMessage should include("No default service discovery implementation configured")
      } finally TestKit.shutdownActorSystem(sys)
    }

    "select implementation from config by classname" in {
      val className = classOf[FakeTestDiscovery].getCanonicalName

      val sys = ActorSystem("DiscoveryConfigurationSpec", ConfigFactory.parseString(s"""
          akka.discovery.method = $className
        """).withFallback(ConfigFactory.load()))

      try ServiceDiscovery(sys).discovery.getClass.getCanonicalName should ===(className)
      finally TestKit.shutdownActorSystem(sys)
    }

    "select implementation from config by config name (inside akka.discovery namespace)" in {
      val className = classOf[FakeTestDiscovery].getCanonicalName

      val sys = ActorSystem("DiscoveryConfigurationSpec", ConfigFactory.parseString(s"""
            akka.discovery {
              method = akka-mock-inside

              akka-mock-inside {
                class = $className
              }
            }
        """).withFallback(ConfigFactory.load()))

      try ServiceDiscovery(sys).discovery.getClass.getCanonicalName should ===(className)
      finally TestKit.shutdownActorSystem(sys)
    }

    "select implementation from config by config name (outside akka.discovery namespace)" in {
      val className = classOf[FakeTestDiscovery].getCanonicalName

      val sys = ActorSystem("DiscoveryConfigurationSpec", ConfigFactory.parseString(s"""
            akka.discovery {
              method = com.akka-mock-outside
            }

            com.akka-mock-outside {
              class = $className
            }
        """).withFallback(ConfigFactory.load()))

      try ServiceDiscovery(sys).discovery.getClass.getCanonicalName should ===(className)
      finally TestKit.shutdownActorSystem(sys)
    }

    "select implementation from full class name" in {
      val className = classOf[FakeTestDiscovery].getCanonicalName

      val sys = ActorSystem("DiscoveryConfigurationSpec", ConfigFactory.parseString(s"""
            akka.discovery {
              method = "$className"
            }
        """).withFallback(ConfigFactory.load()))

      try ServiceDiscovery(sys).discovery.getClass.getCanonicalName should ===(className)
      finally TestKit.shutdownActorSystem(sys)
    }

    "load another implementation from config by config name" in {
      val className1 = classOf[FakeTestDiscovery].getCanonicalName
      val className2 = classOf[FakeTestDiscovery2].getCanonicalName

      val sys = ActorSystem("DiscoveryConfigurationSpec", ConfigFactory.parseString(s"""
            akka.discovery {
              method = mock1

              mock1 {
                class = $className1
              }
              mock2 {
                class = $className2
              }
            }
        """).withFallback(ConfigFactory.load()))

      try {
        ServiceDiscovery(sys).discovery.getClass.getCanonicalName should ===(className1)
        ServiceDiscovery(sys).loadServiceDiscovery("mock2").getClass.getCanonicalName should ===(className2)
      } finally TestKit.shutdownActorSystem(sys)
    }
  }

}

class FakeTestDiscovery extends SimpleServiceDiscovery {
  override def lookup(name: String, resolveTimeout: FiniteDuration): Future[SimpleServiceDiscovery.Resolved] =
    ???
}

class FakeTestDiscovery2 extends FakeTestDiscovery
