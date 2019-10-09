/*
 * Copyright (C) 2017-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.management.cluster.bootstrap

import akka.annotation.InternalApi

/**
 * Internal API
 */
@InternalApi
private[akka] object ClusterBootstrapLogClass {
  val BootstrapCore: Class[ClusterBootstrap] = classOf[ClusterBootstrap]
}
