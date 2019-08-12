/*
 * Copyright (C) 2017-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.cluster.bootstrap

import akka.actor.{ Actor, ActorLogging, ActorSystem, PoisonPill, Props }
import akka.cluster.ClusterEvent.ClusterDomainEvent
import akka.cluster.singleton.{ ClusterSingletonManager, ClusterSingletonManagerSettings }
import akka.cluster.{ Cluster, ClusterEvent }
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.management.cluster.bootstrap.ClusterBootstrap
import akka.management.scaladsl.AkkaManagement
import akka.stream.ActorMaterializer

object ClusterApp {

  def main(args: Array[String]): Unit = {

    implicit val system = ActorSystem()
    implicit val materializer = ActorMaterializer()
    implicit val executionContext = system.dispatcher
    val cluster = Cluster(system)

    system.log.info("Starting Akka Management")
    AkkaManagement(system).start()
    ClusterBootstrap(system).start()

    system.actorOf(
      ClusterSingletonManager.props(
        Props[NoisySingleton],
        PoisonPill,
        ClusterSingletonManagerSettings(system)
      )
    )
    Cluster(system).subscribe(
      system.actorOf(Props[ClusterWatcher]),
      ClusterEvent.InitialStateAsEvents,
      classOf[ClusterDomainEvent]
    )

    // add real app routes here
    val routes =
      path("hello") {
        get {
          complete(
            HttpEntity(ContentTypes.`text/html(UTF-8)`, "<h1>Hello</h1>")
          )
        }
      }

    Http().bindAndHandle(routes, "0.0.0.0", 8080)

    system.log.info(
      s"Server online at http://localhost:8080/\nPress RETURN to stop..."
    )

    cluster.registerOnMemberUp(() => {
      system.log.info("Cluster member is up!")
    })
  }

  class ClusterWatcher extends Actor with ActorLogging {
    val cluster = Cluster(context.system)

    override def receive = {
      case msg => log.info(s"Cluster ${cluster.selfAddress} >>> " + msg)
    }
  }
}
