/*
 * Copyright (C) 2017-2025 Lightbend Inc. <https://akka.io>
 */

package akka.management.cluster

import akka.actor.Address
import akka.cluster.{ ClusterEvent, Member, MemberStatus, UniqueAddress }
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.sse.ServerSentEvent
import spray.json.{ DefaultJsonProtocol, JsArray, JsNumber, JsObject, JsString, JsValue }

/**
 * Encodes a supplied `ClusterEvent.ClusterDomainEvent` into a `ServerSentEvent`.
 */
object ClusterDomainEventServerSentEventEncoder extends SprayJsonSupport with DefaultJsonProtocol {
  def encode(event: ClusterEvent.ClusterDomainEvent): Option[ServerSentEvent] = {
    def sse(eventType: String, value: JsObject): Some[ServerSentEvent] =
      Some(
        ServerSentEvent(
          data = value.copy(fields = value.fields.updated("type", JsString(eventType))).compactPrint,
          eventType = Some(eventType)
        )
      )

    event match {
      case memberEvent: ClusterEvent.MemberEvent =>
        memberEvent match {
          case ClusterEvent.MemberJoined(member) =>
            sse("MemberJoined", JsObject("member" -> encode(member)))

          case ClusterEvent.MemberWeaklyUp(member) =>
            sse("MemberWeaklyUp", JsObject("member" -> encode(member)))

          case ClusterEvent.MemberUp(member) =>
            sse("MemberUp", JsObject("member" -> encode(member)))

          case ClusterEvent.MemberLeft(member) =>
            sse("MemberLeft", JsObject("member" -> encode(member)))

          case ClusterEvent.MemberExited(member) =>
            sse("MemberExited", JsObject("member" -> encode(member)))

          case ClusterEvent.MemberDowned(member) =>
            sse("MemberDowned", JsObject("member" -> encode(member)))

          case ClusterEvent.MemberRemoved(member, previousStatus) =>
            sse("MemberRemoved", JsObject("member" -> encode(member), "previousStatus" -> encode(previousStatus)))

          case _ =>
            None
        }

      case leaderChanged: ClusterEvent.LeaderChanged =>
        leaderChanged.leader match {
          case Some(address) =>
            sse("LeaderChanged", JsObject("address" -> encode(address)))

          case None =>
            sse("LeaderChanged", JsObject.empty)
        }

      case roleLeaderChanged: ClusterEvent.RoleLeaderChanged =>
        roleLeaderChanged.leader match {
          case Some(address) =>
            sse(
              "RoleLeaderChanged",
              JsObject("role" -> JsString(roleLeaderChanged.role), "address" -> encode(address))
            )

          case None =>
            sse("RoleLeaderChanged", JsObject("role" -> JsString(roleLeaderChanged.role)))
        }

      case ClusterEvent.ClusterShuttingDown =>
        sse("ClusterShuttingDown", JsObject.empty)

      case reachabilityEvent: ClusterEvent.ReachabilityEvent =>
        reachabilityEvent match {
          case ClusterEvent.UnreachableMember(member) =>
            sse("UnreachableMember", JsObject("member" -> encode(member)))

          case ClusterEvent.ReachableMember(member) =>
            sse("ReachableMember", JsObject("member" -> encode(member)))
        }

      case dataCenterReachabilityEvent: ClusterEvent.DataCenterReachabilityEvent =>
        dataCenterReachabilityEvent match {
          case ClusterEvent.UnreachableDataCenter(dataCenter) =>
            sse("UnreachableDataCenter", JsObject("dataCenter" -> JsString(dataCenter)))
          case ClusterEvent.ReachableDataCenter(dataCenter) =>
            sse("ReachableDataCenter", JsObject("dataCenter" -> JsString(dataCenter)))
        }

      case _ =>
        // these are either internal events we don't want to expose, or new events
        // that this code is unaware of. either way, let's ignore them

        None
    }
  }

  private def encode(address: Address): JsValue = JsString(address.toString)

  private def encode(address: UniqueAddress): JsValue = JsObject(
    "address" -> encode(address.address),
    "longUid" -> JsNumber(address.longUid)
  )

  private def encode(memberStatus: MemberStatus): JsValue = JsString(
    memberStatus match {
      case MemberStatus.Joining  => "Joining"
      case MemberStatus.WeaklyUp => "WeaklyUp"
      case MemberStatus.Up       => "Up"
      case MemberStatus.Leaving  => "Leaving"
      case MemberStatus.Exiting  => "Exiting"
      case MemberStatus.Down     => "Down"
      case MemberStatus.Removed  => "Removed"
      case other                 => other.toString
    }
  )

  private def encode(member: Member): JsValue = JsObject(
    "uniqueAddress" -> encode(member.uniqueAddress),
    "status" -> encode(member.status),
    "roles" -> JsArray(member.roles.toVector.map(JsString.apply)),
    "dataCenter" -> JsString(member.dataCenter)
  )
}
