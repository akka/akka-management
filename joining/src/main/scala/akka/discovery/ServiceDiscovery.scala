/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.discovery

import akka.actor.{ ActorSystem, DeadLetterSuppression }

import scala.collection.immutable
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

object ServiceDiscovery {
  final case class Resolved(serviceName: String, addresses: immutable.Seq[ResolvedTarget])
      extends DeadLetterSuppression
  final case class ResolvedTarget(host: String, port: Option[Int])
}

trait ServiceDiscovery {
  import ServiceDiscovery._
  def lookup(name: String, resolveTimeout: FiniteDuration): Future[Resolved]
}
