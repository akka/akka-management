/*
 * Copyright (C) 2017-2025 Lightbend Inc. <https://akka.io>
 */

package akka.cluster.bootstrap

import akka.actor.ActorSystem
import akka.management.cluster.bootstrap.ClusterBootstrap
import akka.management.scaladsl.AkkaManagement

object Ec2DemoApp extends App {

  implicit val system: ActorSystem = ActorSystem("demo")

  AkkaManagement(system).start()

  ClusterBootstrap(system).start()

}
