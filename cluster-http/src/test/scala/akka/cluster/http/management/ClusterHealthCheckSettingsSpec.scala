/*
 * Copyright (C) 2017-2018 Lightbend Inc. <http://www.lightbend.com>
 */

package akka.cluster.http.management

import akka.cluster.MemberStatus
import akka.management.cluster.ClusterHealthCheckRoutes
import org.scalatest.{ Matchers, WordSpec }

class ClusterHealthCheckSettingsSpec extends WordSpec with Matchers {

  "Member status parsing" must {
    "be case insensitive" in {
      ClusterHealthCheckRoutes.memberStatus("WeaklyUp") shouldEqual MemberStatus.WeaklyUp
      ClusterHealthCheckRoutes.memberStatus("Weaklyup") shouldEqual MemberStatus.WeaklyUp
      ClusterHealthCheckRoutes.memberStatus("weaklyUp") shouldEqual MemberStatus.WeaklyUp
      ClusterHealthCheckRoutes.memberStatus("Up") shouldEqual MemberStatus.Up
      ClusterHealthCheckRoutes.memberStatus("Exiting") shouldEqual MemberStatus.Exiting
      ClusterHealthCheckRoutes.memberStatus("down") shouldEqual MemberStatus.Down
      ClusterHealthCheckRoutes.memberStatus("joininG") shouldEqual MemberStatus.Joining
      ClusterHealthCheckRoutes.memberStatus("leaving") shouldEqual MemberStatus.Leaving
      ClusterHealthCheckRoutes.memberStatus("removed") shouldEqual MemberStatus.Removed
    }

    "have a useful error message for invalid values" in {

      intercept[IllegalArgumentException] {
        ClusterHealthCheckRoutes.memberStatus("cats") shouldEqual MemberStatus.Removed
      }.getMessage shouldEqual "'cats' is not a valid MemberStatus. See reference.conf for valid values"
    }
  }

}
