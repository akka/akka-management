/*
 * Copyright (C) 2017-2023 Lightbend Inc. <https://www.lightbend.com>
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
