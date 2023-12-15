/*
 * Copyright (C) 2017-2023 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.cluster.bootstrap

import akka.actor.ActorSystem
import akka.cluster.Cluster
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.management.cluster.bootstrap.ClusterBootstrap
import akka.management.scaladsl.AkkaManagement
import akka.rollingupdate.kubernetes.AppVersionRevision
import akka.rollingupdate.kubernetes.PodDeletionCost

object PodDeletionCostDemoApp extends App {

  implicit val system: ActorSystem = ActorSystem("akka-rolling-update-demo")

  import system.log
  val cluster = Cluster(system)

  log.info(s"Started [$system], cluster.selfAddress = ${cluster.selfAddress}")

  AkkaManagement(system).start()

  // preferred to be called before ClusterBootstrap
  AppVersionRevision(system).start()

  ClusterBootstrap(system).start()

  PodDeletionCost(system).start()

  Http().newServerAt("0.0.0.0", 8080).bind(complete("Hello world"))
}
