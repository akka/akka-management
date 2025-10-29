/*
 * Copyright (C) 2017-2025 Lightbend Inc. <https://akka.io>
 */

import akka.actor.Address
import akka.cluster.MemberStatus
import akka.cluster.{ ClusterEvent, Member, UniqueAddress }
import akka.http.scaladsl.model.sse.ServerSentEvent
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import akka.util.Version

package akka.cluster.management {

  // `Member`'s constructor is private[cluster], hence this
  object TestHelpers {
    def newMember(
        uniqueAddress: UniqueAddress,
        upNumber: Int,
        status: MemberStatus,
        roles: Set[String],
        appVersion: Version): Member =
      new Member(uniqueAddress, upNumber, status, roles, appVersion)
  }
}

package akka.management.cluster {

  class ClusterDomainEventServerSentEventEncoderSpec extends AnyWordSpec with Matchers {
    import akka.cluster.management.TestHelpers._

    "Encoder" must {
      "work" in {
        val dc = "dc-one"
        val address1 = Address("akka", "Main", "hostname.com", 3311)
        val uniqueAddress1 = UniqueAddress(address1, 1L)
        val appVersion = Version("2.3.4")

        ClusterDomainEventServerSentEventEncoder.encode(
          ClusterEvent.MemberJoined(
            newMember(uniqueAddress1, 1, MemberStatus.Joining, Set("one", "two", dc), appVersion))
        ) shouldEqual Some(
          ServerSentEvent(
            """{"member":{"dataCenter":"one","roles":["one","two","dc-one"],"status":"Joining","uniqueAddress":{"address":"akka://Main@hostname.com:3311","longUid":1}},"type":"MemberJoined"}""",
            "MemberJoined"
          )
        )

        ClusterDomainEventServerSentEventEncoder.encode(
          ClusterEvent.MemberWeaklyUp(
            newMember(uniqueAddress1, 1, MemberStatus.WeaklyUp, Set("one", "two", dc), appVersion))
        ) shouldEqual Some(
          ServerSentEvent(
            """{"member":{"dataCenter":"one","roles":["one","two","dc-one"],"status":"WeaklyUp","uniqueAddress":{"address":"akka://Main@hostname.com:3311","longUid":1}},"type":"MemberWeaklyUp"}""",
            "MemberWeaklyUp"
          )
        )

        ClusterDomainEventServerSentEventEncoder.encode(
          ClusterEvent.MemberUp(newMember(uniqueAddress1, 1, MemberStatus.Up, Set("one", "two", dc), appVersion))
        ) shouldEqual Some(
          ServerSentEvent(
            """{"member":{"dataCenter":"one","roles":["one","two","dc-one"],"status":"Up","uniqueAddress":{"address":"akka://Main@hostname.com:3311","longUid":1}},"type":"MemberUp"}""",
            "MemberUp"
          )
        )

        ClusterDomainEventServerSentEventEncoder.encode(
          ClusterEvent.MemberLeft(newMember(uniqueAddress1, 1, MemberStatus.Leaving, Set("one", "two", dc), appVersion))
        ) shouldEqual Some(
          ServerSentEvent(
            """{"member":{"dataCenter":"one","roles":["one","two","dc-one"],"status":"Leaving","uniqueAddress":{"address":"akka://Main@hostname.com:3311","longUid":1}},"type":"MemberLeft"}""",
            "MemberLeft"
          )
        )

        ClusterDomainEventServerSentEventEncoder.encode(
          ClusterEvent.MemberExited(
            newMember(uniqueAddress1, 1, MemberStatus.Exiting, Set("one", "two", dc), appVersion))
        ) shouldEqual Some(
          ServerSentEvent(
            """{"member":{"dataCenter":"one","roles":["one","two","dc-one"],"status":"Exiting","uniqueAddress":{"address":"akka://Main@hostname.com:3311","longUid":1}},"type":"MemberExited"}""",
            "MemberExited"
          )
        )

        ClusterDomainEventServerSentEventEncoder.encode(
          ClusterEvent.MemberDowned(newMember(uniqueAddress1, 1, MemberStatus.Down, Set("one", "two", dc), appVersion))
        ) shouldEqual Some(
          ServerSentEvent(
            """{"member":{"dataCenter":"one","roles":["one","two","dc-one"],"status":"Down","uniqueAddress":{"address":"akka://Main@hostname.com:3311","longUid":1}},"type":"MemberDowned"}""",
            "MemberDowned"
          )
        )

        ClusterDomainEventServerSentEventEncoder.encode(
          ClusterEvent.MemberRemoved(
            newMember(uniqueAddress1, 1, MemberStatus.Removed, Set("one", "two", dc), appVersion),
            MemberStatus.Down)
        ) shouldEqual Some(
          ServerSentEvent(
            """{"member":{"dataCenter":"one","roles":["one","two","dc-one"],"status":"Removed","uniqueAddress":{"address":"akka://Main@hostname.com:3311","longUid":1}},"previousStatus":"Down","type":"MemberRemoved"}""",
            "MemberRemoved"
          )
        )

        ClusterDomainEventServerSentEventEncoder.encode(
          ClusterEvent.LeaderChanged(Some(address1))
        ) shouldEqual Some(
          ServerSentEvent(
            """{"address":"akka://Main@hostname.com:3311","type":"LeaderChanged"}""",
            "LeaderChanged"
          )
        )

        ClusterDomainEventServerSentEventEncoder.encode(
          ClusterEvent.RoleLeaderChanged("test-role", Some(address1))
        ) shouldEqual Some(
          ServerSentEvent(
            """{"address":"akka://Main@hostname.com:3311","role":"test-role","type":"RoleLeaderChanged"}""",
            "RoleLeaderChanged"
          )
        )

        ClusterDomainEventServerSentEventEncoder.encode(
          ClusterEvent.ClusterShuttingDown
        ) shouldEqual Some(
          ServerSentEvent(
            """{"type":"ClusterShuttingDown"}""",
            "ClusterShuttingDown"
          )
        )

        ClusterDomainEventServerSentEventEncoder.encode(
          ClusterEvent.UnreachableMember(
            newMember(uniqueAddress1, 1, MemberStatus.Up, Set("one", "two", dc), appVersion))
        ) shouldEqual Some(
          ServerSentEvent(
            """{"member":{"dataCenter":"one","roles":["one","two","dc-one"],"status":"Up","uniqueAddress":{"address":"akka://Main@hostname.com:3311","longUid":1}},"type":"UnreachableMember"}""",
            "UnreachableMember"
          )
        )

        ClusterDomainEventServerSentEventEncoder.encode(
          ClusterEvent.ReachableMember(newMember(uniqueAddress1, 1, MemberStatus.Up, Set("one", "two", dc), appVersion))
        ) shouldEqual Some(
          ServerSentEvent(
            """{"member":{"dataCenter":"one","roles":["one","two","dc-one"],"status":"Up","uniqueAddress":{"address":"akka://Main@hostname.com:3311","longUid":1}},"type":"ReachableMember"}""",
            "ReachableMember"
          )
        )

        ClusterDomainEventServerSentEventEncoder.encode(
          ClusterEvent.UnreachableDataCenter(dc)
        ) shouldEqual Some(
          ServerSentEvent(
            """{"dataCenter":"dc-one","type":"UnreachableDataCenter"}""",
            "UnreachableDataCenter"
          )
        )

        ClusterDomainEventServerSentEventEncoder.encode(
          ClusterEvent.ReachableDataCenter(dc)
        ) shouldEqual Some(
          ServerSentEvent(
            """{"dataCenter":"dc-one","type":"ReachableDataCenter"}""",
            "ReachableDataCenter"
          )
        )
      }
    }

  }
}
