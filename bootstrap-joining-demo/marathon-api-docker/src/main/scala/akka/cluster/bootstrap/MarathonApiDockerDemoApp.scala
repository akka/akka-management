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

object MarathonApiDockerDemoApp extends App {

  implicit val system = ActorSystem("simple")

  AkkaManagement(system).start()
  ClusterBootstrap(system).start()

}

