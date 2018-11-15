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
import akka.stream.scaladsl.Sink
import akka.management.http._
import akka.stream.scaladsl.Source
import com.typesafe.config.ConfigFactory

object DemoApp extends App {

  implicit val system = ActorSystem("simple")

  import system.log
  import system.dispatcher
  implicit val mat = ActorMaterializer()
  val cluster = Cluster(system)

  log.info("Started [{}], cluster.selfAddress = {}", system, cluster.selfAddress)

  AkkaManagement(system).start()
  ClusterBootstrap(system).start()

  cluster
    .subscribe(system.actorOf(Props[ClusterWatcher]), ClusterEvent.InitialStateAsEvents, classOf[ClusterDomainEvent])

  import akka.http.scaladsl.server.Directives._
  Http().bindAndHandle(complete("Hello world"), "0.0.0.0", 8080)

}

class ClusterWatcher extends Actor with ActorLogging {
  val cluster = Cluster(context.system)

  override def receive = {
    case msg ⇒ log.info("Cluster {} >>> {}", msg, cluster.selfAddress)
  }
}
