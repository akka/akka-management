/*
 * Copyright (C) 2017 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.cluster.http.management

import java.net.InetAddress

import com.typesafe.config.Config

final class ClusterHttpManagementSettings(val config: Config) {
  private val cc = config.getConfig("akka.cluster.http.management")
  val ClusterHttpManagementPort = cc.getInt("port")
  val ClusterHttpManagementHostname = {
    val hostname = cc.getString("hostname")
    if (hostname == "")
      InetAddress.getLocalHost.getHostAddress
    else
      hostname
  }
}
