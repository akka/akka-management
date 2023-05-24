/*
 * Copyright (C) 2017-2023 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.rollingupdate.kubernetes

import akka.actor.ActorSystem
import akka.testkit.TestKit
import com.typesafe.config.ConfigFactory
import org.scalatest.concurrent.Eventually
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
object AppVersionRevisionSpec {
  val config = ConfigFactory.parseString("""
      akka.loggers = ["akka.testkit.TestEventListener"]
      akka.actor.provider = cluster

      akka.remote.artery.canonical.port = 0
      akka.remote.artery.canonical.hostname = 127.0.0.1

      akka.cluster.jmx.multi-mbeans-in-same-jvm = on
      akka.coordinated-shutdown.terminate-actor-system = off
      akka.coordinated-shutdown.run-by-actor-system-terminate = off
      akka.test.filter-leeway = 10s
      akka.rollingupdate.kubernetes {
       api-ca-path = ""
       api-token-path = ""
       api-service-host = ""
       api-service-port = 0
       namespace = ""
       namespace-path = ""
       pod-name = ""
       secure-api-server = false
       api-service-request-timeout = 2s
      }
    """)
}
class AppVersionRevisionSpec
    extends TestKit(
      ActorSystem(
        "AppVersionRevisionSpec",
        KubernetesApiSpec.config
      ))
    with AnyWordSpecLike
    with Matchers
    with Eventually
    with ScalaFutures {

  "AppVersionRevision extension" should {
    "return failed future if pod-name not configured" in {
      val revisionExtension = AppVersionRevision(system)
      revisionExtension.start()
      val failure = revisionExtension.getRevision().failed.futureValue
      failure.getMessage should include("No configuration found to extract the pod name from")
    }
  }
}
