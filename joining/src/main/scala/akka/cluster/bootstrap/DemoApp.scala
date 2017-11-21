/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.cluster.bootstrap

import akka.actor.{ Actor, ActorLogging, ActorSystem, Props }
import akka.cluster.ClusterEvent.ClusterDomainEvent
import akka.cluster.{ Cluster, ClusterEvent }
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory

object DemoApp extends App {

  implicit val system = ActorSystem("DemoApp", ConfigFactory.parseString("""
       akka.actor.provider = "cluster"
    """).withFallback(ConfigFactory.load()))

  import system.log
  import system.dispatcher
  implicit val mat = ActorMaterializer()
  implicit val cluster = Cluster(system)

  log.info(s"Started [$system], cluster.selfAddress = ${cluster.selfAddress}")

  ClusterBootstrap(system).start()

  cluster
    .subscribe(system.actorOf(Props[ClusterWatcher]), ClusterEvent.InitialStateAsEvents, classOf[ClusterDomainEvent])

  import akka.http.scaladsl.server.Directives._
  Http().bindAndHandle(complete("Hello world"), "0.0.0.0", 8080)

}

class ClusterWatcher extends Actor with ActorLogging {
  implicit val cluster = Cluster(context.system)

  override def receive = {
    case msg ⇒ log.info(s"Cluster ${cluster.selfAddress} >>> " + msg)
  }
}
