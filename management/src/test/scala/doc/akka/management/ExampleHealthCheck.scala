/*
 * Copyright (C) 2017-2018 Lightbend Inc. <http://www.lightbend.com>
 */

package doc.akka.management

import akka.actor.ActorSystem
import akka.cluster.{ Cluster, MemberStatus }

import scala.concurrent.Future

//#basic
class ExampleHealthCheck(system: ActorSystem) extends (() => Future[Boolean]) {
  override def apply(): Future[Boolean] = {
    Future.successful(true)
  }
}
//#basic

//#cluster
class ClusterHealthCheck(system: ActorSystem) extends (() => Future[Boolean]) {
  private val cluster = Cluster(system)
  override def apply(): Future[Boolean] = {
    Future.successful(cluster.selfMember.status == MemberStatus.Up)
  }
}
//#cluster
