/*
 * Copyright (C) 2017-2024 Lightbend Inc. <https://www.lightbend.com>
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

object ClusterApp {

  def main(args: Array[String]): Unit = {

    implicit val system: ActorSystem = ActorSystem()
    val cluster = Cluster(system)

    system.log.info("Starting Akka Management")
    AkkaManagement(system).start()
    ClusterBootstrap(system).start()

    system.actorOf(
      ClusterSingletonManager.props(
        NoisySingleton.props(),
        PoisonPill,
        ClusterSingletonManagerSettings(system)
      )
    )
    Cluster(system).subscribe(
      system.actorOf(ClusterWatcher.props()),
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

    Http().newServerAt("0.0.0.0", 8080).bind(routes)

    system.log.info(
      s"Server online at http://localhost:8080/\nPress RETURN to stop..."
    )

    cluster.registerOnMemberUp(() => {
      system.log.info("Cluster member is up!")
    })
  }

  object ClusterWatcher {
    def props(): Props = Props(new ClusterWatcher)
  }

  class ClusterWatcher extends Actor with ActorLogging {
    val cluster = Cluster(context.system)

    override def receive = {
      case msg => log.info(s"Cluster ${cluster.selfAddress} >>> " + msg)
    }
  }
}
