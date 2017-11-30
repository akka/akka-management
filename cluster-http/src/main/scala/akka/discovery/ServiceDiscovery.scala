/*
 * Copyright (C) 2017 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.discovery

import akka.actor.DeadLetterSuppression
import akka.annotation.ApiMayChange

import scala.collection.immutable
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

@ApiMayChange
object ServiceDiscovery {
  final case class Resolved(serviceName: String, addresses: immutable.Seq[ResolvedTarget])
      extends DeadLetterSuppression
  final case class ResolvedTarget(host: String, port: Option[Int])
}

@ApiMayChange
trait ServiceDiscovery {
  import ServiceDiscovery._
  def lookup(name: String, resolveTimeout: FiniteDuration): Future[Resolved]
}
