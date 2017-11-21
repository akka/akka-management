/*
 * Copyright (C) 2017 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.management

import java.net.InetAddress

import com.typesafe.config.Config
import scala.collection.JavaConverters._

final class AkkaManagementSettings(val config: Config) {
  private val managementConfig = config.getConfig("akka.management")

  object Http {
    private val cc = managementConfig.getConfig("http")

    val Hostname: String = {
      val hostname = cc.getString("hostname")
      if (hostname == "") InetAddress.getLocalHost.getHostAddress
      else hostname
    }

    val Port: Int = {
      val p = cc.getInt("port")
      require(p >= 0, s"akka.management.http.port MUST be > 0 (was ${p})")
      p
    }

    def isHttps = false // FIXME

    val BasePath: Option[String] =
      Option(cc.getString("base-path")).flatMap(it ⇒ if (it.trim == "") None else Some(it))

    val RouteProviders: List[String] = cc.getStringList("route-providers").asScala.toList

  }

}
