/*
 * Copyright (C) 2017-2018 Lightbend Inc. <http://www.lightbend.com>
 */

package akka.management

import java.net.InetAddress
import java.util.Optional

import scala.collection.JavaConverters._
import scala.collection.immutable
import scala.compat.java8.OptionConverters._

import akka.annotation.InternalApi
import com.typesafe.config.Config

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
      require(0 to 65535 contains p, s"akka.management.http.port must be 0 through 65535 (was ${p})")
      p
    }

    val EffectiveBindHostname: String = cc.getString("bind-hostname") match {
      case "" ⇒ Hostname
      case value ⇒ value
    }

    val EffectiveBindPort: Int = cc.getString("bind-port") match {
      case "" ⇒ Port
      case value ⇒
        val p = value.toInt
        require(0 to 65535 contains p, s"akka.management.http.bind-port must be 0 through 65535 (was ${p})")
        p
    }

    val BasePath: Option[String] =
      Option(cc.getString("base-path")).flatMap(it ⇒ if (it.trim == "") None else Some(it))

    val RouteProviders: immutable.Seq[String] = cc.getStringList("route-providers").asScala.toList

  }

  /** Java API */
  def getHttpHostname: String = Http.Hostname

  /** Java API */
  def getHttpPort: Int = Http.Port

  /** Java API */
  def getHttpEffectiveBindHostname: String = Http.EffectiveBindHostname

  /** Java API */
  def getHttpEffectiveBindPort: Int = Http.EffectiveBindPort

  /** Java API */
  def getBasePath: Optional[String] = Http.BasePath.asJava

  /** Java API */
  def getHttpRouteProviders: java.util.List[String] = Http.RouteProviders.asJava

}

/**
 * INTERNAL API
 */
@InternalApi private[akka] object AkkaManagementSettings {

  implicit class HasDefined(val config: Config) {
    def hasDefined(key: String): Boolean =
      config.hasPath(key) &&
      config.getString(key).trim.nonEmpty &&
      config.getString(key) != s"<$key>"

    def optDefinedValue(key: String): Option[String] =
      if (hasDefined(key)) Some(config.getString(key)) else None

    def optValue(key: String): Option[String] =
      config.getString(key) match {
        case "" => None
        case other => Some(other)
      }
  }
}
