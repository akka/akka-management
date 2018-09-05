/*
 * Copyright (C) 2017 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.discovery.marathon

import akka.actor.ActorSystem
import akka.discovery.SimpleServiceDiscovery.ResolvedTarget
import akka.discovery.marathon.mock.MarathonApi
import akka.testkit.TestKit
import org.scalatest.{AsyncFlatSpecLike, BeforeAndAfterAll, Matchers}

class MarathonApiSimpleServiceDiscoverySpec
    extends TestKit(ActorSystem("MarathonApiServiceDiscoveryBehaviour"))
    with MarathonApiServiceDiscoveryBehaviour
    with AsyncFlatSpecLike
    with BeforeAndAfterAll
    with Matchers {

  import MarathonApiServiceDiscoveryBehaviour._

  val `marathon_1.6` = new MarathonApi(
    port = 4200,
    marathonResponsesPath = "mappings",
    marathonVersion = "marathon_1.6"
  )

  override def beforeAll(): Unit = {
    `marathon_1.6`.start()
  }

  override def afterAll(): Unit = {
    `marathon_1.6`.stop()
    TestKit.shutdownActorSystem(system)
  }

  it should behave like discoveryForPods(
    api = `marathon_1.6`,
    expectations = DiscoveryForPodsExpectations(
      mesosHost = Seq(
        ResolvedTarget(host = "192.168.65.111", port = Some(1443))
      ),
      mesosContainerSingle = Seq(
        ResolvedTarget(host = "9.0.1.30", port = Some(443)),
        ResolvedTarget(host = "9.0.1.29", port = Some(443))
      ),
      mesosContainerMultiple = Seq(
        ResolvedTarget(host = "192.199.1.14", port = Some(443))
      )
    )
  )

  it should behave like discoveryForApps(
    api = `marathon_1.6`,
    expectations = DiscoveryForAppsExpectations(
      dockerHost = Seq(
        ResolvedTarget(host = "192.168.65.60", port = Some(443))
      ),
      mesosHost = Seq(
        ResolvedTarget(host = "192.168.65.60", port = Some(443))
      ),
      dockerContainerSingle = Seq(
        ResolvedTarget(host = "9.0.2.130", port = Some(443))
      ),
      mesosContainerSingle = Seq(
        ResolvedTarget(host = "9.0.2.34", port = Some(443))
      ),
      mesosContainerMultiple = Seq(
        ResolvedTarget(host = "192.199.2.16", port = Some(443))
      ),
      dockerBridge = Seq(
        ResolvedTarget(host = "172.17.0.2", port = Some(22950))
      ),
      mesosBridge = Seq(
        ResolvedTarget(host = "172.31.254.12", port = Some(15200))
      )
    )
  )

  it should behave like discoveryForSingleCluster(
    api = `marathon_1.6`,
    expectations = DiscoveryForSingleClusterExpectations(
      anySubnet = AkkaClusterExpectations(
        standaloneDockerApps = Seq(
          ResolvedTarget(host = "172.17.0.2", port = Some(20549)),
          ResolvedTarget(host = "9.0.1.130", port = Some(443)),
          ResolvedTarget(host = "192.168.65.60", port = Some(443))
        ),
        standaloneMesosApps = Seq(
          ResolvedTarget(host = "172.31.254.5", port = Some(5732)),
          ResolvedTarget(host = "192.199.1.4", port = Some(443)),
          ResolvedTarget(host = "9.0.1.8", port = Some(443)),
          ResolvedTarget(host = "192.168.65.60", port = Some(443))
        ),
        standaloneMesosPods = Seq(
          ResolvedTarget(host = "192.199.1.6", port = Some(443)),
          ResolvedTarget(host = "9.0.2.16", port = Some(443)),
          ResolvedTarget(host = "9.0.1.16", port = Some(443)),
          ResolvedTarget(host = "192.168.65.111", port = Some(1443))
        ),
        mixedDockerApps = Seq(
          ResolvedTarget(host = "9.0.2.130", port = Some(443)),
          ResolvedTarget(host = "192.168.65.60", port = Some(443)),
          ResolvedTarget(host = "172.17.0.2", port = Some(11567))
        ),
        mixedMesosApps = Seq(
          ResolvedTarget(host = "9.0.2.38", port = Some(443)),
          ResolvedTarget(host = "9.0.1.39", port = Some(443)),
          ResolvedTarget(host = "192.168.65.60", port = Some(443)),
          ResolvedTarget(host = "172.31.254.13", port = Some(18803)),
          ResolvedTarget(host = "192.199.1.16", port = Some(443))
        ),
        mixedMesosPods = Seq(
          ResolvedTarget(host = "192.168.65.112", port = Some(1443)),
          ResolvedTarget(host = "192.199.1.17", port = Some(443)),
          ResolvedTarget(host = "9.0.1.43", port = Some(443)),
          ResolvedTarget(host = "9.0.1.45", port = Some(443))
        )
      ),
      specificSubnet = AkkaClusterExpectations(
        standaloneDockerApps = Seq.empty,
        standaloneMesosApps = Seq(
          ResolvedTarget(host = "192.199.1.4", port = Some(443))
        ),
        standaloneMesosPods = Seq(
          ResolvedTarget(host = "192.199.1.6", port = Some(443))
        ),
        mixedDockerApps = Seq.empty,
        mixedMesosApps = Seq(
          ResolvedTarget(host = "192.199.1.16", port = Some(443))
        ),
        mixedMesosPods = Seq(
          ResolvedTarget(host = "192.199.1.17", port = Some(443))
        )
      )
    )
  )

  it should behave like discoveryForMultipleClusters(
    api = `marathon_1.6`,
    expectations = DiscoveryForMultipleClustersExpectations(
      clusterOne = AkkaClusterExpectations(
        standaloneDockerApps = Seq(
          ResolvedTarget(host = "9.0.1.130", port = Some(443)),
          ResolvedTarget(host = "192.168.65.60", port = Some(443))
        ),
        standaloneMesosApps = Seq(
          ResolvedTarget(host = "172.31.254.5", port = Some(20554)),
          ResolvedTarget(host = "9.0.1.17", port = Some(443))
        ),
        standaloneMesosPods = Seq(
          ResolvedTarget(host = "192.199.1.7", port = Some(443)),
          ResolvedTarget(host = "192.168.65.112", port = Some(1443))
        ),
        mixedDockerApps = Seq(
          ResolvedTarget(host = "9.0.2.130", port = Some(443)),
          ResolvedTarget(host = "192.168.65.60", port = Some(443))
        ),
        mixedMesosApps = Seq(
          ResolvedTarget(host = "9.0.1.50", port = Some(443)),
          ResolvedTarget(host = "9.0.1.51", port = Some(443)),
          ResolvedTarget(host = "172.31.254.15", port = Some(2291))
        ),
        mixedMesosPods = Seq(
          ResolvedTarget(host = "192.168.65.111", port = Some(1443)),
          ResolvedTarget(host = "192.199.1.18", port = Some(443))
        )
      ),
      clusterTwo = AkkaClusterExpectations(
        standaloneDockerApps = Seq(
          ResolvedTarget(host = "172.17.0.2", port = Some(28558))
        ),
        standaloneMesosApps = Seq(
          ResolvedTarget(host = "192.199.2.10", port = Some(443)),
          ResolvedTarget(host = "192.168.65.60", port = Some(443))
        ),
        standaloneMesosPods = Seq(
          ResolvedTarget(host = "9.0.1.18", port = Some(443)),
          ResolvedTarget(host = "9.0.2.19", port = Some(443))
        ),
        mixedDockerApps = Seq(
          ResolvedTarget(host = "172.17.0.2", port = Some(15529))
        ),
        mixedMesosApps = Seq(
          ResolvedTarget(host = "192.168.65.60", port = Some(443)),
          ResolvedTarget(host = "192.199.1.19", port = Some(443))
        ),
        mixedMesosPods = Seq(
          ResolvedTarget(host = "9.0.2.41", port = Some(443)),
          ResolvedTarget(host = "9.0.1.48", port = Some(443))
        )
      )
    )
  )

  it should behave like discoveryWithMisconfiguredServices(api = `marathon_1.6`)
}
