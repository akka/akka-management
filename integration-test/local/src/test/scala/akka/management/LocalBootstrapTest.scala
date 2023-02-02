/*
 * Copyright (C) 2017-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.management

import scala.concurrent.duration._

import akka.actor.ActorSystem
import akka.cluster.Cluster
import akka.cluster.MemberStatus
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.StatusCode
import akka.http.scaladsl.model.StatusCodes
import akka.management.cluster.bootstrap.ClusterBootstrap
import akka.management.scaladsl.AkkaManagement
import akka.testkit.SocketUtil
import akka.testkit.TestKit
import com.typesafe.config.ConfigFactory
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.Eventually
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.Millis
import org.scalatest.time.Seconds
import org.scalatest.time.Span
import org.scalatest.wordspec.AnyWordSpec

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

class LocalBootstrapTest extends AnyWordSpec with ScalaFutures with Matchers with Eventually with BeforeAndAfterAll {
  import LocalBootstrapTest._

  implicit override val patienceConfig: PatienceConfig =
    PatienceConfig(
      timeout = scaled(Span(20, Seconds)),
      interval = scaled(Span(500, Millis))
    )

  private var systems = Seq.empty[ActorSystem]

  def newSystem(managementPort: Int): ActorSystem =
    ActorSystem(
      "local-cluster",
      ConfigFactory.parseString(s"""
      akka.management.http.port = $managementPort
      akka.coordinated-shutdown.exit-jvm = off
       """.stripMargin).withFallback(config)
    )

  override def afterAll(): Unit = {
    // TODO: shutdown Akka HTTP connection pools. Requires Akka HTTP 10.2
    systems.reverse.foreach { sys =>
      TestKit.shutdownActorSystem(sys, 3.seconds)
    }
    super.afterAll()
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
    systems = managementPorts.map(newSystem)
    val clusters = systems.map(Cluster.apply)
    systems.foreach(AkkaManagement(_).start())
    // for http client
    implicit val system: ActorSystem = systems.head

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
        clusters.foreach(c =>
          c.state.members.toList.map(_.status) shouldEqual List(
            MemberStatus.Up,
            MemberStatus.Up,
            MemberStatus.Up
          ))
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
