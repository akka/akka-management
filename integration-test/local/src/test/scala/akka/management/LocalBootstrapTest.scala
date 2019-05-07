/*
 * Copyright (C) 2017-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.management

import akka.actor.ActorSystem
import akka.cluster.{ Cluster, MemberStatus }
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ HttpRequest, StatusCode, StatusCodes }
import akka.management.cluster.bootstrap.ClusterBootstrap
import akka.management.scaladsl.AkkaManagement
import akka.testkit.SocketUtil
import com.typesafe.config.ConfigFactory
import org.scalatest.concurrent.{ Eventually, ScalaFutures }
import org.scalatest.time.{ Seconds, Span }
import org.scalatest.{ Matchers, WordSpec }

object LocalBootstrapTest {
  val managementPorts = SocketUtil.temporaryServerAddresses(3, "127.0.0.1").map(_.getPort)
  // See src/main/resources/application.conf for bootstrap settings which are used in docs so needs tested
  val config = ConfigFactory.parseString(s"""
      akka.remote.artery {
        enabled = on
        transport = tcp
        canonical {
          hostname = localhost
          port = 0
        }
      }
      akka.management {
        http.hostname = "127.0.0.1"
        cluster.bootstrap.contact-point-discovery.port-name = "management"
      }
      akka.discovery {
        config.services = {
          local-cluster = {
            endpoints = [
              {
                host = "127.0.0.1"
                port = ${managementPorts(0)}
              },
              {
                host = "127.0.0.1"
                port = ${managementPorts(1)}
              },
              {
                host = "127.0.0.1"
                port = ${managementPorts(2)}
              }
            ]
          }
        }
      }
    """).withFallback(ConfigFactory.load())
}

class LocalBootstrapTest extends WordSpec with ScalaFutures with Matchers with Eventually {
  import LocalBootstrapTest._

  implicit override val patienceConfig: PatienceConfig =
    PatienceConfig(
      timeout = scaled(Span(10, Seconds)),
      interval = scaled(Span(1, Seconds))
    )

  def newSystem(managementPort: Int): ActorSystem = {
    ActorSystem("local-cluster", ConfigFactory.parseString(s"""
      akka.management.http.port = $managementPort
       """.stripMargin).withFallback(config))
  }

  def readyStatusCode(port: Int)(implicit system: ActorSystem): StatusCode =
    healthCheckStatus(port, "health/ready")
  def aliveStatusCode(port: Int)(implicit system: ActorSystem): StatusCode =
    healthCheckStatus(port, "health/alive")

  def healthCheckStatus(port: Int, path: String)(
      implicit system: ActorSystem
  ): StatusCode = {
    Http().singleRequest(HttpRequest(uri = s"http://localhost:$port/$path")).futureValue.status
  }

  "Cluster bootstrap with health checks" should {
    val systems = managementPorts.map(newSystem)
    val clusters = systems.map(Cluster.apply)
    systems.foreach(AkkaManagement(_).start())
    // for http client
    implicit val system = systems(0)

    "not be ready initially" in {
      eventually {
        managementPorts.foreach { port =>
          readyStatusCode(port) shouldEqual StatusCodes.InternalServerError
        }
      }
    }

    "be alive initially" in {
      eventually {
        managementPorts.foreach { port =>
          aliveStatusCode(port) shouldEqual StatusCodes.OK
        }
      }
    }
    "form a cluster" in {
      systems.foreach(ClusterBootstrap(_).start())
      eventually {
        clusters.foreach(
          c =>
            c.state.members.toList.map(_.status) shouldEqual List(
              MemberStatus.Up,
              MemberStatus.Up,
              MemberStatus.Up
          )
        )
      }
    }

    "be ready after formation" in {
      eventually {
        managementPorts.foreach { port =>
          readyStatusCode(port) shouldEqual StatusCodes.OK
        }
      }
    }
  }
}
