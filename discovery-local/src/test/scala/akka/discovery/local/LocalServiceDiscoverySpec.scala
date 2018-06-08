/*
 * Copyright (C) 2017 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.discovery.local

import java.util.UUID

import akka.actor.ActorSystem
import akka.discovery.SimpleServiceDiscovery.{ Resolved, ResolvedTarget }
import akka.discovery.local.registration.LocalServiceEntry
import com.typesafe.config.ConfigFactory
import org.scalatest.{ Matchers, WordSpec }

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Success

class LocalServiceDiscoverySpec extends WordSpec with Matchers {

  val serviceFile = s"${UUID.randomUUID()}.json"

  val config = ConfigFactory.parseString(s"""
      |akka.discovery {
      |  method = akka-local
      |  akka-local {
      |    class = ${classOf[LocalServiceDiscovery].getName}
      |    service-file = $serviceFile
      |  }
      |}
      |akka.remote.netty.tcp.hostname = 127.0.0.1
      |akka.management.http.port = 8559
      |""".stripMargin)

  implicit val system: ActorSystem = ActorSystem("local-test", config)

  "LocalServiceDiscovery" should {
    "resolve 127.0.0.1:8559" in {
      import scala.concurrent.ExecutionContext.Implicits.global
      val serviceDiscovery = new LocalServiceDiscovery(system)
      serviceDiscovery.lookup("local-test", 3.seconds).map { resolved =>
        resolved shouldBe Resolved("local-test", List(ResolvedTarget("127.0.0.1", Some(8559))))
      }
    }
  }

}
