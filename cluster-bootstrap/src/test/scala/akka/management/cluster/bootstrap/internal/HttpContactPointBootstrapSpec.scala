/*
 * Copyright (C) 2017-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.management.cluster.bootstrap.internal

import akka.actor.ActorPath
import akka.http.scaladsl.model.Uri.Host

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class HttpContactPointBootstrapSpec extends AnyWordSpec with Matchers {
  "HttpContactPointBootstrap" should {
    "use a safe name when connecting over IPv6" in {
      val name = HttpContactPointBootstrap.name(Host("[fe80::1013:2070:258a:c662]"), 443)
      ActorPath.isValidPathElement(name) should be(true)
    }
  }
}
