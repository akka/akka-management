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

class KubernetesHealthChecks(system: ActorSystem) {

  val log: LoggingAdapter = Logging(system, getClass)
  val cluster = Cluster(system)

  //#health
  private val readyStates: Set[MemberStatus] = Set(MemberStatus.Up, MemberStatus.WeaklyUp)

  // Removed is the initial state before joining. See https://github.com/akka/akka/issues/25663
  private val aliveStates: Set[MemberStatus] =
    Set(MemberStatus.Joining, MemberStatus.WeaklyUp, MemberStatus.Up, MemberStatus.Leaving, MemberStatus.Exiting, MemberStatus.Removed)

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
            val selfState = cluster.selfMember.status
            log.debug("alive? clusterState {}", selfState)
            if (aliveStates.contains(selfState)) complete(StatusCodes.OK)
            else complete(StatusCodes.InternalServerError)
          }
        })
  //#health
}
