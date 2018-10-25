/*
 * Copyright (C) 2017 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.cluster.bootstrap

import akka.actor.ActorSystem
import akka.cluster.{ Cluster, MemberStatus }
import akka.event.{ Logging, LoggingAdapter }
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route

class KubernetesHealthCheck(system: ActorSystem) {

  val log: LoggingAdapter = Logging(system, getClass)
  val cluster = Cluster(system)

  //#health
  private val readyStates: Set[MemberStatus] = Set(MemberStatus.Up, MemberStatus.WeaklyUp)

  val k8sHealthChecks: Route =
    concat(
        path("ready") {
          get {
            val selfState = cluster.selfMember.status
            log.debug("ready? clusterState {}", selfState)
            if (readyStates.contains(selfState)) complete(StatusCodes.OK)
            else complete(StatusCodes.InternalServerError)
          }
        },
        path("alive") {
          get {
            // When Akka HTTP can respond to requests, that is sufficient
            // to consider ourselves 'live': we don't want K8s to kill us even
            // when we're in the process of shutting down (only stop sending
            // us traffic, which is done due to the readyness check then failing)
            complete(StatusCodes.OK)
          }
        })
  //#health
}
