/*
 * Copyright (C) 2017-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.rollingupdate.kubernetes

import akka.actor.ActorSystem
import akka.actor.Address
import akka.actor.Props
import akka.cluster.Cluster
import akka.cluster.ClusterEvent.MemberUp
import akka.cluster.Member
import akka.cluster.MemberStatus
import akka.cluster.MemberStatus.Up
import akka.cluster.UniqueAddress
import akka.testkit.EventFilter
import akka.testkit.ImplicitSender
import akka.testkit.TestKit
import akka.testkit.TestProbe
import akka.util.Version
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.MappingBuilder
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.patch
import com.github.tomakehurst.wiremock.client.WireMock.patchRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.verify
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.github.tomakehurst.wiremock.matching.ContainsPattern
import com.github.tomakehurst.wiremock.matching.EqualToPattern
import com.typesafe.config.ConfigFactory
import org.scalatest.BeforeAndAfterAll
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.Millis
import org.scalatest.time.Seconds
import org.scalatest.time.Span
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.duration._
import scala.collection.JavaConverters._

class PodDeletionCostAnnotatorSpec
    extends TestKit(
      ActorSystem(
        "MySpec",
        ConfigFactory.parseString("""
    akka.loggers = ["akka.testkit.TestEventListener"]
    akka.actor.provider = cluster
    akka.rollingupdate.kubernetes.pod-deletion-cost.retry-delay = 1s

    akka.coordinated-shutdown.terminate-actor-system = off
    akka.coordinated-shutdown.run-by-actor-system-terminate = off
    """)
      ))
    with ImplicitSender
    with AnyWordSpecLike
    with Matchers
    with BeforeAndAfterAll
    with BeforeAndAfterEach
    with Eventually {

  private val wireMockServer = new WireMockServer(wireMockConfig().port(0))
  wireMockServer.start()

  private val namespace = "namespace-test"
  private val podName = "pod-test"
  private val settings = new KubernetesSettings(
    apiCaPath = "",
    apiTokenPath = "",
    apiServiceHost = "localhost",
    apiServicePort = wireMockServer.port(),
    namespace = Some(namespace),
    namespacePath = "",
    podName = podName,
    secure = false)

  WireMock.configureFor(settings.apiServicePort)

  private val annotatorActorProps = Props(
    classOf[PodDeletionCostAnnotator],
    settings,
    "apiToken",
    namespace,
    PodDeletionCostSettings(system.settings.config.getConfig("akka.rollingupdate.kubernetes"))
  )

  override implicit val patienceConfig =
    PatienceConfig(timeout = Span(5, Seconds), interval = Span(100, Millis))

  override protected def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  override protected def beforeEach(): Unit = {
    wireMockServer.resetAll()
  }

  private val podK8sPath = urlEqualTo("/api/v1/namespaces/" + namespace + "/pods/" + podName)

  private def stubWithReturnCode(returnCode: Int) =
    stubFor(patchPodDeletionCost().willReturn(aResponse().withStatus(returnCode)))

  private def patchPodDeletionCost(): MappingBuilder =
    patch(podK8sPath)
      .withHeader("Content-Type", new EqualToPattern("application/merge-patch+json"))
      .withRequestBody(new ContainsPattern(
        """{"metadata": {"annotations": {"controller.kubernetes.io/pod-deletion-cost": "10000" }}}"""))

  "The pod-deletion-cost annotator, when under normal behavior" should {

    "have a single node cluster running first" in {
      val probe = TestProbe()
      Cluster(system).join(Cluster(system).selfMember.address)
      probe.awaitAssert({
        Cluster(system).selfMember.status == MemberStatus.Up
      }, 3.seconds)

    }

    "correctly annotate the cluster node" in {
      stubWithReturnCode(200)
      expectLogInfo(pattern = ".*Updating pod-deletion-cost annotation.*") {
        system.actorOf(annotatorActorProps)
      }
      eventually {
        verify(1, patchRequestedFor(podK8sPath))
      }
    }

    "give up when failing with non-transient error" in {
      stubWithReturnCode(404)
      expectLogError(pattern = ".*Not retrying, check configuration.*") {
        system.actorOf(annotatorActorProps)
      }
    }

    "retry when failing with transient error" in {
      val scenarioName = "RetryScenario"

      // first call fails
      stubFor(
        patchPodDeletionCost()
          .inScenario(scenarioName)
          .whenScenarioStateIs("FAILING")
          .willReturn(aResponse().withStatus(500))
          .willSetStateTo("AVAILABLE"))

      // second call succeeds
      stubFor(
        patchPodDeletionCost()
          .inScenario(scenarioName)
          .whenScenarioStateIs("AVAILABLE")
          .willReturn(aResponse().withStatus(200))
          .willSetStateTo("OK"))
      wireMockServer.setScenarioState(scenarioName, "FAILING") // set starting state to failing

      assertState(scenarioName, "FAILING")
      system.actorOf(annotatorActorProps)
      assertState(scenarioName, "AVAILABLE")
      // after the retry backoff delay
      assertState(scenarioName, "OK")
    }

  }

  "The pod-deletion-cost annotator, when under retry backoff" should {

    "have a single node cluster running first" in {
      val probe = TestProbe()
      Cluster(system).join(Cluster(system).selfMember.address)
      probe.awaitAssert({
        Cluster(system).selfMember.status == MemberStatus.Up
      }, 3.seconds)
    }

    "not annotate until backoff delay expires" in {
      val scenarioName = "RetryScenario"

      // first call fails
      stubFor(
        patchPodDeletionCost()
          .inScenario(scenarioName)
          .whenScenarioStateIs("FAILING")
          .willReturn(aResponse().withStatus(500))
          .willSetStateTo("AVAILABLE"))
      wireMockServer.setScenarioState(scenarioName, "FAILING") // set starting state to failing

      // second call succeeds
      stubFor(
        patchPodDeletionCost()
          .inScenario(scenarioName)
          .whenScenarioStateIs("AVAILABLE")
          .willReturn(aResponse().withStatus(200))
          .willSetStateTo("OK"))

      assertState(scenarioName, "FAILING")
      val underTest = system.actorOf(annotatorActorProps)
      assertState(scenarioName, "AVAILABLE")

      val dummyNewMember =
        MemberUp(Member(UniqueAddress(Address("", ""), 2L), Set("dc-default"), Version("v1")).copy(Up))
      underTest ! dummyNewMember
      underTest ! dummyNewMember
      underTest ! dummyNewMember

      // no other interactions should have occurred while on backoff regardless of updates to the cluster
      verify(1, patchRequestedFor(podK8sPath))

      assertState(scenarioName, "OK")
      verify(2, patchRequestedFor(podK8sPath))
    }
  }

  private def assertState(scenarioName: String, state: String) = eventually {
    val scenario = wireMockServer.getAllScenarios.getScenarios.asScala.toList.find(_.getName == scenarioName).get
    scenario.getState should ===(state)
  }
  def expectLogInfo[T](pattern: String = null)(block: => T): T =
    EventFilter.info(pattern = pattern, occurrences = 1).intercept(block)(system)

  def expectLogError[T](pattern: String = null)(block: => T): T =
    EventFilter.error(pattern = pattern, occurrences = 1).intercept(block)(system)

}
