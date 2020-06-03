/*
 * Copyright (C) 2017-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.coordination.lease.kubernetes

import akka.coordination.lease.TimeoutSettings
import com.typesafe.config.ConfigFactory
import org.scalatest.{ Matchers, WordSpec }
import scala.concurrent.duration._

class KubernetesSettingsSpec extends WordSpec with Matchers {

  private def conf(overrides: String): KubernetesSettings = {
    val c = ConfigFactory
      .parseString(overrides)
      .withFallback(ConfigFactory.load().getConfig("akka.coordination.lease.kubernetes"))
    KubernetesSettings(c, TimeoutSettings(c))
  }

  "Kubernetes Settings" should {
    "default request-timeout is 2/5 of the lease-operation-timeout" in {
      conf("lease-operation-timeout=5s").apiServerRequestTimeout shouldEqual 2.seconds
    }
    "body-read timoeut is 1/2 api request timeout" in {
      conf("lease-operation-timeout=5s").bodyReadTimeout shouldEqual 1.seconds
    }
    "allow overriding of api server request timeout" in {
      conf("""
           lease-operation-timeout=5s
           api-server-request-timeout=4s
        """.stripMargin).apiServerRequestTimeout shouldEqual 4.seconds
    }
    "not allow server request timeout greater than operation timeout" in {
      intercept[IllegalArgumentException] {
        conf("""
           lease-operation-timeout=5s
           api-server-request-timeout=6s
        """.stripMargin).apiServerRequestTimeout
      }.getMessage shouldEqual "requirement failed: 'api-server-request-timeout can not be less than 'lease-operation-timeout'"
    }
  }

}
