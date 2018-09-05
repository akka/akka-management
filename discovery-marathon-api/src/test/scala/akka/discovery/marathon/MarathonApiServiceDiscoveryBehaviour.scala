/*
 * Copyright (C) 2017 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.discovery.marathon

import scala.concurrent.duration._

import akka.actor.ActorSystem
import akka.discovery.SimpleServiceDiscovery.ResolvedTarget
import akka.discovery.marathon.mock.MarathonApi
import akka.http.scaladsl.{ Http, HttpExt }
import org.apache.commons.net.util.SubnetUtils
import org.scalatest.{ AsyncFlatSpecLike, Matchers }

object MarathonApiServiceDiscoveryBehaviour {
  final case class DiscoveryForPodsExpectations(
      mesosHost: Seq[ResolvedTarget],
      mesosContainerSingle: Seq[ResolvedTarget],
      mesosContainerMultiple: Seq[ResolvedTarget]
  )

  final case class DiscoveryForAppsExpectations(
      dockerHost: Seq[ResolvedTarget],
      mesosHost: Seq[ResolvedTarget],
      dockerContainerSingle: Seq[ResolvedTarget],
      mesosContainerSingle: Seq[ResolvedTarget],
      mesosContainerMultiple: Seq[ResolvedTarget],
      dockerBridge: Seq[ResolvedTarget],
      mesosBridge: Seq[ResolvedTarget]
  )

  final case class DiscoveryForSingleClusterExpectations(
      anySubnet: AkkaClusterExpectations,
      specificSubnet: AkkaClusterExpectations
  )

  final case class DiscoveryForMultipleClustersExpectations(
      clusterOne: AkkaClusterExpectations,
      clusterTwo: AkkaClusterExpectations
  )

  final case class AkkaClusterExpectations(
      standaloneDockerApps: Seq[ResolvedTarget],
      standaloneMesosApps: Seq[ResolvedTarget],
      standaloneMesosPods: Seq[ResolvedTarget],
      mixedDockerApps: Seq[ResolvedTarget],
      mixedMesosApps: Seq[ResolvedTarget],
      mixedMesosPods: Seq[ResolvedTarget]
  )
}

trait MarathonApiServiceDiscoveryBehaviour { _: AsyncFlatSpecLike with Matchers =>

  import MarathonApiServiceDiscoveryBehaviour._

  protected implicit val system: ActorSystem

  private val defaultSettings: Settings = Settings(
    marathonApiUrl = "none",
    servicePortName = "akkamgmthttp",
    serviceLabelName = "ACTOR_SYSTEM_NAME",
    expectedClusterSubnet = None
  )

  private implicit val defaultTimeout: FiniteDuration = 3.seconds

  private implicit val http: HttpExt = Http()(system)

  def discoveryForPods(api: MarathonApi, expectations: DiscoveryForPodsExpectations): Unit = {
    it should s"discover pods [${api.marathonVersion}] [mesos containerizer] [host networking]" in {
      val discovery = new ServiceDiscovery.ForPods(
        defaultSettings.copy(marathonApiUrl = s"${api.url}/mesos/host/single")
      )

      for {
        result <- discovery.resolveTargets("test-system-1")
      } yield {
        result should be(expectations.mesosHost)
      }
    }

    it should s"discover pods [${api.marathonVersion}] [mesos containerizer] [container networking] [single network]" in {
      val discovery = new ServiceDiscovery.ForPods(
        defaultSettings.copy(marathonApiUrl = s"${api.url}/mesos/container/single")
      )

      for {
        result <- discovery.resolveTargets("test-system-1")
      } yield {
        result should be(expectations.mesosContainerSingle)
      }
    }

    it should s"discover pods [${api.marathonVersion}] [mesos containerizer] [container networking] [multiple networks]" in {
      val discovery = new ServiceDiscovery.ForPods(
        defaultSettings.copy(
          marathonApiUrl = s"${api.url}/mesos/container-multinetwork/single",
          expectedClusterSubnet = Some(new SubnetUtils("192.199.0.0/16").getInfo) // testcn
        )
      )

      for {
        result <- discovery.resolveTargets("test-system-1")
      } yield {
        result should be(expectations.mesosContainerMultiple)
      }
    }
  }

  def discoveryForApps(api: MarathonApi, expectations: DiscoveryForAppsExpectations): Unit = {
    it should s"discover apps [${api.marathonVersion}] [docker containerizer] [host networking]" in {
      val discovery = new ServiceDiscovery.ForApps(
        defaultSettings.copy(marathonApiUrl = s"${api.url}/docker/host/single")
      )

      for {
        result <- discovery.resolveTargets("test-system-1")
      } yield {
        result should be(expectations.dockerHost)
      }
    }

    it should s"discover apps [${api.marathonVersion}] [mesos containerizer] [host networking]" in {
      val discovery = new ServiceDiscovery.ForApps(
        defaultSettings.copy(marathonApiUrl = s"${api.url}/mesos/host/single")
      )

      for {
        result <- discovery.resolveTargets("test-system-1")
      } yield {
        result should be(expectations.mesosHost)
      }
    }

    it should s"discover apps [${api.marathonVersion}] [docker containerizer] [container networking]" in {
      val discovery = new ServiceDiscovery.ForApps(
        defaultSettings.copy(marathonApiUrl = s"${api.url}/docker/container/single")
      )

      for {
        result <- discovery.resolveTargets("test-system-1")
      } yield {
        result should be(expectations.dockerContainerSingle)
      }
    }

    it should s"discover apps [${api.marathonVersion}] [mesos containerizer] [container networking] [single network]" in {
      val discovery = new ServiceDiscovery.ForApps(
        defaultSettings.copy(marathonApiUrl = s"${api.url}/mesos/container/single")
      )

      for {
        result <- discovery.resolveTargets("test-system-1")
      } yield {
        result should be(expectations.mesosContainerSingle)
      }
    }

    it should s"discover apps [${api.marathonVersion}] [mesos containerizer] [container networking] [multiple networks]" in {
      val discovery = new ServiceDiscovery.ForApps(
        defaultSettings.copy(
          marathonApiUrl = s"${api.url}/mesos/container-multinetwork/single",
          expectedClusterSubnet = Some(new SubnetUtils("192.199.0.0/16").getInfo) // testcn
        )
      )

      for {
        result <- discovery.resolveTargets("test-system-1")
      } yield {
        result should be(expectations.mesosContainerMultiple)
      }
    }

    it should s"discover apps [${api.marathonVersion}] [docker containerizer] [bridge networking]" in {
      val discovery = new ServiceDiscovery.ForApps(
        defaultSettings.copy(marathonApiUrl = s"${api.url}/docker/bridge/single")
      )

      for {
        result <- discovery.resolveTargets("test-system-1")
      } yield {
        result should be(expectations.dockerBridge)
      }
    }

    it should s"discover apps [${api.marathonVersion}] [mesos containerizer] [bridge networking]" in {
      val discovery = new ServiceDiscovery.ForApps(
        defaultSettings.copy(marathonApiUrl = s"${api.url}/mesos/bridge/single")
      )

      for {
        result <- discovery.resolveTargets("test-system-1")
      } yield {
        result should be(expectations.mesosBridge)
      }
    }
  }

  def discoveryForSingleCluster(api: MarathonApi, expectations: DiscoveryForSingleClusterExpectations): Unit = {
    it should s"discover single akka cluster without other services [${api.marathonVersion}] [docker containerizer] [apps] [any subnet]" in {
      val dockerAppDiscovery = new ServiceDiscovery.ForApps(
        defaultSettings.copy(marathonApiUrl = s"${api.url}/docker/all/single")
      )

      for {
        targets <- dockerAppDiscovery.resolveTargets("test-system-1")
      } yield {
        targets should be(expectations.anySubnet.standaloneDockerApps)
      }
    }

    it should s"discover single akka cluster without other services [${api.marathonVersion}] [docker containerizer] [apps] [specific subnet]" in {
      val dockerAppDiscovery = new ServiceDiscovery.ForApps(
        defaultSettings.copy(
          marathonApiUrl = s"${api.url}/docker/all/single",
          expectedClusterSubnet = Some(new SubnetUtils("192.199.0.0/16").getInfo) // testcn
        )
      )

      for {
        targets <- dockerAppDiscovery.resolveTargets("test-system-1")
      } yield {
        targets should be(expectations.specificSubnet.standaloneDockerApps)
      }
    }

    it should s"discover single akka cluster without other services [${api.marathonVersion}] [mesos containerizer] [apps] [any subnet]" in {
      val mesosAppDiscovery = new ServiceDiscovery.ForApps(
        defaultSettings.copy(marathonApiUrl = s"${api.url}/mesos/all/single")
      )

      for {
        targets <- mesosAppDiscovery.resolveTargets("test-system-1")
      } yield {
        targets should be(expectations.anySubnet.standaloneMesosApps)
      }
    }

    it should s"discover single akka cluster without other services [${api.marathonVersion}] [mesos containerizer] [apps] [specific subnet]" in {
      val mesosAppDiscovery = new ServiceDiscovery.ForApps(
        defaultSettings.copy(
          marathonApiUrl = s"${api.url}/mesos/all/single",
          expectedClusterSubnet = Some(new SubnetUtils("192.199.0.0/16").getInfo) // testcn
        )
      )

      for {
        targets <- mesosAppDiscovery.resolveTargets("test-system-1")
      } yield {
        targets should be(expectations.specificSubnet.standaloneMesosApps)
      }
    }

    it should s"discover single akka cluster without other services [${api.marathonVersion}] [mesos containerizer] [pods] [any subnet]" in {
      val mesosPodDiscovery = new ServiceDiscovery.ForPods(
        defaultSettings.copy(marathonApiUrl = s"${api.url}/mesos/all/single")
      )

      for {
        targets <- mesosPodDiscovery.resolveTargets("test-system-1")
      } yield {
        targets should be(expectations.anySubnet.standaloneMesosPods)
      }
    }

    it should s"discover single akka cluster without other services [${api.marathonVersion}] [mesos containerizer] [pods] [specific subnet]" in {
      val mesosPodDiscovery = new ServiceDiscovery.ForPods(
        defaultSettings.copy(
          marathonApiUrl = s"${api.url}/mesos/all/single",
          expectedClusterSubnet = Some(new SubnetUtils("192.199.0.0/16").getInfo) // testcn
        )
      )

      for {
        targets <- mesosPodDiscovery.resolveTargets("test-system-1")
      } yield {
        targets should be(expectations.specificSubnet.standaloneMesosPods)
      }
    }

    it should s"discover single akka cluster with other services [${api.marathonVersion}] [docker containerizer] [apps] [any subnet]" in {
      val dockerAppDiscovery = new ServiceDiscovery.ForApps(
        defaultSettings.copy(marathonApiUrl = s"${api.url}/docker/mixed/single")
      )

      for {
        targets <- dockerAppDiscovery.resolveTargets("test-system-1")
      } yield {
        targets should be(expectations.anySubnet.mixedDockerApps)
      }
    }

    it should s"discover single akka cluster with other services [${api.marathonVersion}] [docker containerizer] [apps] [specific subnet]" in {
      val dockerAppDiscovery = new ServiceDiscovery.ForApps(
        defaultSettings.copy(
          marathonApiUrl = s"${api.url}/docker/mixed/single",
          expectedClusterSubnet = Some(new SubnetUtils("192.199.0.0/16").getInfo) // testcn
        )
      )

      for {
        targets <- dockerAppDiscovery.resolveTargets("test-system-1")
      } yield {
        targets should be(expectations.specificSubnet.mixedDockerApps)
      }
    }

    it should s"discover single akka cluster with other services [${api.marathonVersion}] [mesos containerizer] [apps] [any subnet]" in {
      val mesosAppDiscovery = new ServiceDiscovery.ForApps(
        defaultSettings.copy(marathonApiUrl = s"${api.url}/mesos/mixed/single")
      )

      for {
        targets <- mesosAppDiscovery.resolveTargets("test-system-1")
      } yield {
        targets should be(expectations.anySubnet.mixedMesosApps)
      }
    }

    it should s"discover single akka cluster with other services [${api.marathonVersion}] [mesos containerizer] [apps] [specific subnet]" in {
      val mesosAppDiscovery = new ServiceDiscovery.ForApps(
        defaultSettings.copy(
          marathonApiUrl = s"${api.url}/mesos/mixed/single",
          expectedClusterSubnet = Some(new SubnetUtils("192.199.0.0/16").getInfo) // testcn
        )
      )

      for {
        targets <- mesosAppDiscovery.resolveTargets("test-system-1")
      } yield {
        targets should be(expectations.specificSubnet.mixedMesosApps)
      }
    }

    it should s"discover single akka cluster with other services [${api.marathonVersion}] [mesos containerizer] [pods] [any subnet]" in {
      val mesosPodDiscovery = new ServiceDiscovery.ForPods(
        defaultSettings.copy(marathonApiUrl = s"${api.url}/mesos/mixed/single")
      )

      for {
        targets <- mesosPodDiscovery.resolveTargets("test-system-1")
      } yield {
        targets should be(expectations.anySubnet.mixedMesosPods)
      }
    }

    it should s"discover single akka cluster with other services [${api.marathonVersion}] [mesos containerizer] [pods] [specific subnet]" in {
      val mesosPodDiscovery = new ServiceDiscovery.ForPods(
        defaultSettings.copy(
          marathonApiUrl = s"${api.url}/mesos/mixed/single",
          expectedClusterSubnet = Some(new SubnetUtils("192.199.0.0/16").getInfo) // testcn
        )
      )

      for {
        targets <- mesosPodDiscovery.resolveTargets("test-system-1")
      } yield {
        targets should be(expectations.specificSubnet.mixedMesosPods)
      }
    }
  }

  def discoveryForMultipleClusters(api: MarathonApi, expectations: DiscoveryForMultipleClustersExpectations): Unit = {

    it should s"discover multiple akka clusters without other services [${api.marathonVersion}] [docker containerizer] [apps]" in {
      val dockerAppDiscovery = new ServiceDiscovery.ForApps(
        defaultSettings.copy(marathonApiUrl = s"${api.url}/docker/all/multi")
      )

      for {
        clusterOneTargets <- dockerAppDiscovery.resolveTargets("test-system-1")
        clusterTwoTargets <- dockerAppDiscovery.resolveTargets("test-system-2")
      } yield {
        clusterOneTargets should be(expectations.clusterOne.standaloneDockerApps)
        clusterTwoTargets should be(expectations.clusterTwo.standaloneDockerApps)
      }
    }

    it should s"discover multiple akka clusters without other services [${api.marathonVersion}] [mesos containerizer] [apps]" in {

      val mesosAppDiscovery = new ServiceDiscovery.ForApps(
        defaultSettings.copy(marathonApiUrl = s"${api.url}/mesos/all/multi")
      )

      for {
        clusterOneTargets <- mesosAppDiscovery.resolveTargets("test-system-1")
        clusterTwoTargets <- mesosAppDiscovery.resolveTargets("test-system-2")
      } yield {
        clusterOneTargets should be(expectations.clusterOne.standaloneMesosApps)
        clusterTwoTargets should be(expectations.clusterTwo.standaloneMesosApps)
      }
    }

    it should s"discover multiple akka clusters without other services [${api.marathonVersion}] [mesos containerizer] [pods]" in {
      val mesosPodDiscovery = new ServiceDiscovery.ForPods(
        defaultSettings.copy(marathonApiUrl = s"${api.url}/mesos/all/multi")
      )

      for {
        clusterOneTargets <- mesosPodDiscovery.resolveTargets("test-system-1")
        clusterTwoTargets <- mesosPodDiscovery.resolveTargets("test-system-2")
      } yield {
        clusterOneTargets should be(expectations.clusterOne.standaloneMesosPods)
        clusterTwoTargets should be(expectations.clusterTwo.standaloneMesosPods)
      }
    }

    it should s"discover multiple akka clusters with other services [${api.marathonVersion}] [docker containerizer] [apps]" in {
      val dockerAppDiscovery = new ServiceDiscovery.ForApps(
        defaultSettings.copy(marathonApiUrl = s"${api.url}/docker/mixed/multi")
      )

      for {
        clusterOneTargets <- dockerAppDiscovery.resolveTargets("test-system-1")
        clusterTwoTargets <- dockerAppDiscovery.resolveTargets("test-system-2")
      } yield {
        clusterOneTargets should be(expectations.clusterOne.mixedDockerApps)
        clusterTwoTargets should be(expectations.clusterTwo.mixedDockerApps)
      }
    }

    it should s"discover multiple akka clusters with other services [${api.marathonVersion}] [mesos containerizer] [apps]" in {
      val mesosAppDiscovery = new ServiceDiscovery.ForApps(
        defaultSettings.copy(marathonApiUrl = s"${api.url}/mesos/mixed/multi")
      )

      for {
        clusterOneTargets <- mesosAppDiscovery.resolveTargets("test-system-1")
        clusterTwoTargets <- mesosAppDiscovery.resolveTargets("test-system-2")
      } yield {
        clusterOneTargets should be(expectations.clusterOne.mixedMesosApps)
        clusterTwoTargets should be(expectations.clusterTwo.mixedMesosApps)
      }
    }

    it should s"discover multiple akka clusters with other services [${api.marathonVersion}] [mesos containerizer] [pods]" in {
      val mesosPodDiscovery = new ServiceDiscovery.ForPods(
        defaultSettings.copy(marathonApiUrl = s"${api.url}/mesos/mixed/multi")
      )

      for {
        clusterOneTargets <- mesosPodDiscovery.resolveTargets("test-system-1")
        clusterTwoTargets <- mesosPodDiscovery.resolveTargets("test-system-2")
      } yield {
        clusterOneTargets should be(expectations.clusterOne.mixedMesosPods)
        clusterTwoTargets should be(expectations.clusterTwo.mixedMesosPods)
      }
    }
  }

  def discoveryWithMisconfiguredServices(api: MarathonApi): Unit = {
    it should s"not return results when no apps are present [${api.marathonVersion}]" in {
      val appDiscovery = new ServiceDiscovery.ForApps(
        defaultSettings.copy(marathonApiUrl = s"${api.url}/empty")
      )

      for {
        appTargets <- appDiscovery.resolveTargets("test-system-1")
      } yield {
        appTargets should be(Seq.empty)
      }
    }

    it should s"not return results when no pods are present [${api.marathonVersion}]" in {
      val podDiscovery = new ServiceDiscovery.ForPods(
        defaultSettings.copy(marathonApiUrl = s"${api.url}/empty")
      )

      for {
        podTargets <- podDiscovery.resolveTargets("test-system-1")
      } yield {
        podTargets should be(Seq.empty)
      }
    }

    it should s"not return results if no apps pass filtering [${api.marathonVersion}]" in {
      val appDiscovery = new ServiceDiscovery.ForApps(
        defaultSettings.copy(marathonApiUrl = s"${api.url}/noakka")
      )
      for {
        appTargets <- appDiscovery.resolveTargets("test-system-1")
      } yield {
        appTargets should be(Seq.empty)
      }
    }

    it should s"not return results if no pods pass filtering [${api.marathonVersion}]" in {
      val podDiscovery = new ServiceDiscovery.ForPods(
        defaultSettings.copy(marathonApiUrl = s"${api.url}/noakka")
      )

      for {
        podTargets <- podDiscovery.resolveTargets("test-system-1")
      } yield {
        podTargets should be(Seq.empty)
      }
    }

    it should s"not return results if apps pass filtering but do not have expected ports [${api.marathonVersion}]" in {
      val appDiscovery = new ServiceDiscovery.ForApps(
        defaultSettings.copy(marathonApiUrl = s"${api.url}/misconfigured")
      )

      for {
        appTargets <- appDiscovery.resolveTargets("test-system-1")
      } yield {
        appTargets should be(Seq.empty)
      }
    }

    it should s"not return results if pods pass filtering but do not have expected ports [${api.marathonVersion}]" in {
      val podDiscovery = new ServiceDiscovery.ForPods(
        defaultSettings.copy(marathonApiUrl = s"${api.url}/misconfigured")
      )

      for {
        podTargets <- podDiscovery.resolveTargets("test-system-1")
      } yield {
        podTargets should be(Seq.empty)
      }
    }
  }
}
