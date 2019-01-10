/*
 * Copyright (C) 2017-2018 Lightbend Inc. <http://www.lightbend.com>
 */

package akka.cluster.http.management

import akka.cluster.MemberStatus
import akka.management.cluster.scaladsl.ClusterReadinessCheckSettings
import org.scalatest.{ Matchers, WordSpec }

class ClusterReadinessCheckSettingsSpec extends WordSpec with Matchers {

  "Member status parsing" must {
    "be case insensitive" in {
      ClusterReadinessCheckSettings.memberStatus("WeaklyUp") shouldEqual MemberStatus.WeaklyUp
      ClusterReadinessCheckSettings.memberStatus("Weaklyup") shouldEqual MemberStatus.WeaklyUp
      ClusterReadinessCheckSettings.memberStatus("weaklyUp") shouldEqual MemberStatus.WeaklyUp
      ClusterReadinessCheckSettings.memberStatus("Up") shouldEqual MemberStatus.Up
      ClusterReadinessCheckSettings.memberStatus("Exiting") shouldEqual MemberStatus.Exiting
      ClusterReadinessCheckSettings.memberStatus("down") shouldEqual MemberStatus.Down
      ClusterReadinessCheckSettings.memberStatus("joininG") shouldEqual MemberStatus.Joining
      ClusterReadinessCheckSettings.memberStatus("leaving") shouldEqual MemberStatus.Leaving
      ClusterReadinessCheckSettings.memberStatus("removed") shouldEqual MemberStatus.Removed
    }

    "have a useful error message for invalid values" in {

      intercept[IllegalArgumentException] {
        ClusterReadinessCheckSettings.memberStatus("cats") shouldEqual MemberStatus.Removed
      }.getMessage shouldEqual "'cats' is not a valid MemberStatus. See reference.conf for valid values"
    }
  }

}
