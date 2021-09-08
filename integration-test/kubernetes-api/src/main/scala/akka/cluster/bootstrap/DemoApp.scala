/*
 * Copyright (C) 2017-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.cluster.bootstrap

import akka.actor.{ Actor, ActorLogging, ActorSystem, Props }
import akka.cluster.ClusterEvent.ClusterDomainEvent
import akka.cluster.{ Cluster, ClusterEvent }
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.management.cluster.bootstrap.ClusterBootstrap
//#start-akka-management
import akka.management.scaladsl.AkkaManagement

//#start-akka-management

object DemoApp extends App {

  implicit val system = ActorSystem("Appka")

  import system.log
  val cluster = Cluster(system)

  log.info(s"Started [$system], cluster.selfAddress = ${cluster.selfAddress}")

  //#start-akka-management
  AkkaManagement(system).start()
  //#start-akka-management
  ClusterBootstrap(system).start()

  cluster.subscribe(
    system.actorOf(Props[ClusterWatcher]),
    ClusterEvent.InitialStateAsEvents,
    classOf[ClusterDomainEvent]
  )

  // add real app routes here
  val routes =
    path("hello") {
      get {
        complete(
          HttpEntity(
            ContentTypes.`text/html(UTF-8)`,
            "<h1>Hello</h1>"
          )
        )
      }
    }
  Http().newServerAt("0.0.0.0", 8080).bind(routes)

  Cluster(system).registerOnMemberUp({
    log.info("Cluster member is up!")
  })

}

class ClusterWatcher extends Actor with ActorLogging {
  val cluster = Cluster(context.system)

  override def receive = {
    case msg => log.info(s"Cluster ${cluster.selfAddress} >>> " + msg)
  }
}
