/*
 * Copyright (C) 2017-2025 Lightbend Inc. <https://akka.io>
 */

package doc.akka.management.cluster.bootstrap

import akka.actor.ActorSystem
import akka.management.cluster.bootstrap.ClusterBootstrap
import akka.management.scaladsl.AkkaManagement

object ClusterBootstrapCompileOnly {

  val system = ActorSystem()

  //#start
  // Akka Management hosts the HTTP routes used by bootstrap
  AkkaManagement(system).start()

  // Starting the bootstrap process needs to be done explicitly
  ClusterBootstrap(system).start()
  //#start

}
