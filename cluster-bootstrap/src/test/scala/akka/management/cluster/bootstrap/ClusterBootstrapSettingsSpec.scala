/*
 * Copyright (C) 2017-2018 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.management.cluster.bootstrap

import akka.event.NoLogging
import com.typesafe.config.ConfigFactory

/** Currently just tests recent changes. Please enhance accordingly. */
class ClusterBootstrapSettingsSpec extends AbstractBootstrapSpec {

  val config = ConfigFactory.load()

  "ClusterBootstrapSettings" should {

    "have the expected defaults " in {
      val settings = ClusterBootstrapSettings(config, NoLogging)
      settings.newClusterEnabled should ===(true)
    }

    "have the expected overrides " in {
      val overrides = ConfigFactory.parseString("akka.management.cluster.bootstrap.new-cluster-enabled=off")
      val settings = ClusterBootstrapSettings(overrides.withFallback(config), NoLogging)
      settings.newClusterEnabled should ===(false)
    }

    "fall back to old `form-new-cluster` if present for backward compatibility`" in {
      val settings =
        ClusterBootstrapSettings(config.withFallback(ConfigFactory.parseString("""
          akka.management.cluster.bootstrap {
            form-new-cluster=on
            new-cluster-enabled=off
          }""")), NoLogging)
      settings.newClusterEnabled should ===(true)

    }
  }
}
