/*
 * Copyright (C) 2017 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.cluster.bootstrap

import akka.actor.{ Actor, ActorLogging, ActorSystem, Props }
import akka.cluster.ClusterEvent.ClusterDomainEvent
import akka.cluster.{ Cluster, ClusterEvent }
import akka.http.scaladsl.Http
import akka.management.AkkaManagement
import akka.management.cluster.bootstrap.ClusterBootstrap
import akka.stream.ActorMaterializer

object DemoApp extends App {

  implicit val system = ActorSystem("Appka")

  import system.log
  import system.dispatcher
  implicit val mat = ActorMaterializer()
  val cluster = Cluster(system)

  log.info(s"Started [$system], cluster.selfAddress = ${cluster.selfAddress}")

  //#start-akka-management
  AkkaManagement(system).start()
  //#start-akka-management
  ClusterBootstrap(system).start()

  cluster
    .subscribe(system.actorOf(Props[ClusterWatcher]), ClusterEvent.InitialStateAsEvents, classOf[ClusterDomainEvent])

  val k8sHealthChecks = new KubernetesHealthCheck(system)

  val routes = k8sHealthChecks.k8sHealthChecks // add real app routes here

  import akka.http.scaladsl.server.Directives._
  Http().bindAndHandle(routes, "0.0.0.0", 8080)

  Cluster(system).registerOnMemberUp({
    log.info("Cluster member is up!")
  })

}

class ClusterWatcher extends Actor with ActorLogging {
  val cluster = Cluster(context.system)

  override def receive = {
    case msg ⇒ log.info(s"Cluster ${cluster.selfAddress} >>> " + msg)
  }
}
