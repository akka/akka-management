/*
 * Copyright (C) 2017 Lightbend Inc. <http://www.lightbend.com>
 */
package doc.akka.management.cluster.bootstrap

import akka.actor.ActorSystem
import akka.management.AkkaManagement
import akka.management.cluster.bootstrap.ClusterBootstrap

object ClusterBootstrapCompileOnly {

  val system = ActorSystem()

  //#start
  // Akka Management hosts the HTTP routes used by bootstrap
  AkkaManagement(system).start()

  // Starting the bootstrap process needs to be done explicitly
  ClusterBootstrap(system).start()
  //#start

}

