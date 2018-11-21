/*
 * Copyright (C) 2017-2018 Lightbend Inc. <http://www.lightbend.com>
 */

package akka.management.cluster.bootstrap

import com.typesafe.config.ConfigFactory

/** Currently just tests recent changes. Please enhance accordingly. */
class ClusterBootstrapSettingsSpec extends AbstractBootstrapSpec {

  val config = ConfigFactory.load()

  "ClusterBootstrapSettings" should {

    "have the expected defaults " in {
      val settings = ClusterBootstrapSettings(config)
      settings.newClusterEnabled should ===(true)
      settings.joinDecider.selfDerivedHost.isEmpty should ===(true)
    }

    "have the expected overrides " in {
      val overrides = ConfigFactory.parseString("akka.management.cluster.bootstrap.new-cluster-enabled=off")
      val settings = ClusterBootstrapSettings(overrides.withFallback(config))
      settings.newClusterEnabled should ===(false)
    }

    "detect if an old property is set and fail fast, directing users to the new property" in {
      val overrides = ConfigFactory.parseString("akka.management.cluster.bootstrap.form-new-cluster=on")
      intercept[IllegalArgumentException](ClusterBootstrapSettings(overrides.withFallback(config)))
    }
  }
}
