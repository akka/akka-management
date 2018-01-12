/*
 * Copyright (C) 2017 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.management

import java.net.InetAddress

import com.typesafe.config.Config

import scala.collection.JavaConverters._
import scala.collection.immutable

final class AkkaManagementSettings(val config: Config) {
  private val managementConfig = config.getConfig("akka.management")

  object Http {
    private val cc = managementConfig.getConfig("http")

    val Hostname: String = {
      val hostname = cc.getString("hostname")
      if (hostname == "<hostname>") InetAddress.getLocalHost.getHostAddress
      else if (hostname.trim() == "") InetAddress.getLocalHost.getHostAddress
      else hostname
    }

    val Port: Int = {
      val p = cc.getInt("port")
      require(p >= 0, s"akka.management.http.port MUST be > 0 (was ${p})")
      p
    }

    val BindHostname: String = cc.getString("bind-hostname") match {
      case "" ⇒ Hostname
      case value ⇒ value
    }

    val BindPort: Int = cc.getString("bind-port") match {
      case "" ⇒ Port
      case value ⇒
        val p = value.toInt
        require(p >= 0, s"akka.management.http.bind-port MUST be > 0 (was ${p})")
        p
    }

    val BasePath: Option[String] =
      Option(cc.getString("base-path")).flatMap(it ⇒ if (it.trim == "") None else Some(it))

    val RouteProviders: immutable.Seq[String] = cc.getStringList("route-providers").asScala.toList

  }

}
