/*
 * Copyright (C) 2017-2018 Lightbend Inc. <http://www.lightbend.com>
 */

package akka.discovery.marathon

import akka.discovery.SimpleServiceDiscovery.ResolvedTarget
import org.scalatest.{ Matchers, WordSpec }
import spray.json._
import scala.io.Source

class MarathonApiSimpleServiceDiscoverySpec extends WordSpec with Matchers {
  "targets" should {
    "calculate the correct list of resolved targets" in {
      val data = resourceAsString("apps.json")

      val appList = JsonFormat.appListFormat.read(data.parseJson)

      MarathonApiSimpleServiceDiscovery.targets(appList, "akka-mgmt-http") shouldBe List(
          ResolvedTarget(host = "192.168.65.60", port = Some(23236)),
          ResolvedTarget(host = "192.168.65.111", port = Some(6850)))
    }
    "calculate the correct list of resolved targets for docker" in {
      val data = resourceAsString("docker-app.json")

      val appList = JsonFormat.appListFormat.read(data.parseJson)

      MarathonApiSimpleServiceDiscovery.targets(appList, "akkamgmthttp") shouldBe List(
          ResolvedTarget(host = "10.121.48.204", port = Some(29480)),
          ResolvedTarget(host = "10.121.48.204", port = Some(10136)))
    }
  }

  private def resourceAsString(name: String): String =
    Source.fromInputStream(getClass.getClassLoader.getResourceAsStream(name)).mkString
}
