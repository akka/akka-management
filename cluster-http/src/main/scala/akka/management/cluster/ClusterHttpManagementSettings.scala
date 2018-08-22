/*
 * Copyright (C) 2017 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.management.cluster

import com.typesafe.config.Config

final class ClusterHttpManagementSettings(val config: Config) {
  config.getConfig("akka.management.cluster.http")

  // placeholder for potential future configuration... currently nothing is configured here
}
