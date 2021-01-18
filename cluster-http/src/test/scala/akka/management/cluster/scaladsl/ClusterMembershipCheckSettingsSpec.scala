/*
 * Copyright (C) 2017-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.management.cluster.scaladsl

import akka.cluster.MemberStatus
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class ClusterMembershipCheckSettingsSpec extends AnyWordSpec with Matchers {

  "Member status parsing" must {
    "be case insensitive" in {
      ClusterMembershipCheckSettings.memberStatus("WeaklyUp") shouldEqual MemberStatus.WeaklyUp
      ClusterMembershipCheckSettings.memberStatus("Weaklyup") shouldEqual MemberStatus.WeaklyUp
      ClusterMembershipCheckSettings.memberStatus("weaklyUp") shouldEqual MemberStatus.WeaklyUp
      ClusterMembershipCheckSettings.memberStatus("Up") shouldEqual MemberStatus.Up
      ClusterMembershipCheckSettings.memberStatus("Exiting") shouldEqual MemberStatus.Exiting
      ClusterMembershipCheckSettings.memberStatus("down") shouldEqual MemberStatus.Down
      ClusterMembershipCheckSettings.memberStatus("joininG") shouldEqual MemberStatus.Joining
      ClusterMembershipCheckSettings.memberStatus("leaving") shouldEqual MemberStatus.Leaving
      ClusterMembershipCheckSettings.memberStatus("removed") shouldEqual MemberStatus.Removed
    }

    "have a useful error message for invalid values" in {

      intercept[IllegalArgumentException] {
        ClusterMembershipCheckSettings.memberStatus("cats") shouldEqual MemberStatus.Removed
      }.getMessage shouldEqual "'cats' is not a valid MemberStatus. See reference.conf for valid values"
    }
  }

}
