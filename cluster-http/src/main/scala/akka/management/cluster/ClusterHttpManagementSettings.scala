/*
 * Copyright (C) 2017-2025 Lightbend Inc. <https://akka.io>
 */

package akka.management.cluster

import com.typesafe.config.Config

final class ClusterHttpManagementSettings(val config: Config) {
  config.getConfig("akka.management.cluster")

  // placeholder for potential future configuration... currently nothing is configured here
}
