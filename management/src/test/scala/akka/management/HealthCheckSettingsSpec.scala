/*
 * Copyright (C) 2017-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.management
import com.typesafe.config.ConfigFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class HealthCheckSettingsSpec extends AnyWordSpec with Matchers {

  "Health Check Settings" should {
    "filter out blank fqcn" in {
      HealthCheckSettings(ConfigFactory.parseString("""
         readiness-checks {
          cluster-membership = ""
         }
         liveness-checks {}
         readiness-path = ""
         liveness-path = ""
         check-timeout = 1s
        """)).readinessChecks shouldEqual Nil
      HealthCheckSettings(ConfigFactory.parseString("""
         liveness-checks {
          cluster-membership = ""
         }
         readiness-checks {}
         readiness-path = ""
         liveness-path = ""
         check-timeout = 1s
        """)).readinessChecks shouldEqual Nil
    }
  }

}
