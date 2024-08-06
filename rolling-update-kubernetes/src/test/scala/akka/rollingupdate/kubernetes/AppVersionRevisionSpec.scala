/*
 * Copyright (C) 2017-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.rollingupdate.kubernetes

import akka.actor.ActorSystem
import akka.testkit.TestKit
import com.typesafe.config.ConfigFactory
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

object AppVersionRevisionSpec {
  val config = ConfigFactory.parseString("""
      akka.actor.provider = cluster

      akka.remote.artery.canonical.port = 0
      akka.remote.artery.canonical.hostname = 127.0.0.1

      akka.coordinated-shutdown.terminate-actor-system = off
      akka.coordinated-shutdown.run-by-actor-system-terminate = off
      akka.rollingupdate.kubernetes.pod-name = ""
    """)
}
class AppVersionRevisionSpec
    extends TestKit(
      ActorSystem(
        "AppVersionRevisionSpec",
        AppVersionRevisionSpec.config
      ))
    with AnyWordSpecLike
    with Matchers
    with ScalaFutures {

  "AppVersionRevision extension" should {
    "return failed future if pod-name is not configured" in {
      val revisionExtension = AppVersionRevision(system)
      revisionExtension.start()
      val failure = revisionExtension.getRevision().failed.futureValue
      failure.getMessage should include("No configuration found to extract the pod name from")
    }
  }
}
