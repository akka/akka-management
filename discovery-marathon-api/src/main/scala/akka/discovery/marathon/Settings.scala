/*
 * Copyright (C) 2017 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.discovery.marathon

import akka.actor._
import com.typesafe.config.ConfigException
import org.apache.commons.net.util.SubnetUtils

/**
 * Settings for Marathon API discovery.
 *
 * @param marathonApiUrl base Marathon API URL (e.g. http://marathon.mesos:8080)
 * @param servicePortName name used for filtering ports/endpoints (e.g. akkamgmthttp)
 * @param serviceLabelName label name used for filtering apps/pods (e.g. ACTOR_SYSTEM_NAME)
 * @param expectedClusterSubnet subnet used for filtering service addresses (e.g. 192.168.0.0/24)
 */
final case class Settings(
    marathonApiUrl: String,
    servicePortName: String,
    serviceLabelName: String,
    expectedClusterSubnet: Option[SubnetUtils#SubnetInfo]
) extends Extension

object Settings extends ExtensionId[Settings] with ExtensionIdProvider {

  override def get(system: ActorSystem): Settings = super.get(system)

  override def lookup: Settings.type = Settings

  override def createExtension(
      system: ExtendedActorSystem
  ): Settings = {
    val discoveryConfig =
      system.settings.config.getConfig("akka.discovery.marathon-api")

    val marathonApiUrl: String =
      discoveryConfig.getString("url")

    val servicePortName: String =
      discoveryConfig.getString("service-filter.port-name")

    val serviceLabelName: String =
      discoveryConfig.getString("service-filter.label-name")

    val expectedClusterSubnet: Option[SubnetUtils#SubnetInfo] =
      try {
        val expectedClusterSubnet =
          discoveryConfig.getString("service-filter.expected-cluster-subnet")

        if (expectedClusterSubnet.isEmpty
            || expectedClusterSubnet.startsWith("0.0.0.0")) {
          None
        } else {
          Some(
            new SubnetUtils(expectedClusterSubnet).getInfo
          )
        }
      } catch {
        case _: ConfigException.Missing => None
      }

    Settings(
      marathonApiUrl,
      servicePortName,
      serviceLabelName,
      expectedClusterSubnet
    )
  }
}
