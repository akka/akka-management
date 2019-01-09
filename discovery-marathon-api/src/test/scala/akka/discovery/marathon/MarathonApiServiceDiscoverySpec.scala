/*
 * Copyright (C) 2017-2018 Lightbend Inc. <http://www.lightbend.com>
 */

package akka.discovery.marathon

import java.net.InetAddress

import akka.discovery.ServiceDiscovery.ResolvedTarget
import org.scalatest.{ Matchers, WordSpec }
import spray.json._
import scala.io.Source

class MarathonApiServiceDiscoverySpec extends WordSpec with Matchers {
  "targets" should {
    "calculate the correct list of resolved targets" in {
      val data = resourceAsString("apps.json")

      val appList = JsonFormat.appListFormat.read(data.parseJson)

      MarathonApiServiceDiscovery.targets(appList, "akka-mgmt-http") shouldBe List(
          ResolvedTarget(host = "192.168.65.60", port = Some(23236),
            address = Option(InetAddress.getByName("192.168.65.60"))),
          ResolvedTarget(host = "192.168.65.111", port = Some(6850),
            address = Option(InetAddress.getByName("192.168.65.111"))))
    }
    "calculate the correct list of resolved targets for docker" in {
      val data = resourceAsString("docker-app.json")

      val appList = JsonFormat.appListFormat.read(data.parseJson)

      MarathonApiServiceDiscovery.targets(appList, "akkamgmthttp") shouldBe List(ResolvedTarget(host = "10.121.48.204",
          port = Some(29480), address = Option(InetAddress.getByName("10.121.48.204"))),
        ResolvedTarget(host = "10.121.48.204", port = Some(10136),
          address = Option(InetAddress.getByName("10.121.48.204"))))
    }
  }

  private def resourceAsString(name: String): String =
    Source.fromInputStream(getClass.getClassLoader.getResourceAsStream(name)).mkString
}
