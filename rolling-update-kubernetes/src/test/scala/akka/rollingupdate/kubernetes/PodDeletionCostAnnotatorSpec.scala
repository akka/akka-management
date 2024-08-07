/*
 * Copyright (C) 2017-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.rollingupdate.kubernetes

import scala.collection.JavaConverters._
import scala.concurrent.duration._

import akka.actor.ActorSystem
import akka.actor.Address
import akka.cluster.Cluster
import akka.cluster.ClusterEvent.MemberUp
import akka.cluster.Member
import akka.cluster.MemberStatus
import akka.cluster.UniqueAddress
import akka.testkit.EventFilter
import akka.testkit.ImplicitSender
import akka.testkit.TestKit
import akka.testkit.TestProbe
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

object PodDeletionCostAnnotatorSpec {
  val config = ConfigFactory.parseString("""
      akka.loggers = ["akka.testkit.TestEventListener"]
      akka.actor.provider = cluster
      akka.rollingupdate.kubernetes.pod-deletion-cost.retry-delay = 1s

      akka.remote.artery.canonical.port = 0
      akka.remote.artery.canonical.hostname = 127.0.0.1

      akka.cluster.jmx.multi-mbeans-in-same-jvm = on
      akka.coordinated-shutdown.terminate-actor-system = off
      akka.coordinated-shutdown.run-by-actor-system-terminate = off
      akka.test.filter-leeway = 10s
    """)
}

class PodDeletionCostAnnotatorSpec
    extends TestKit(
      ActorSystem(
        "PodDeletionCostAnnotatorSpec",
        PodDeletionCostAnnotatorSpec.config
      ))
    with ImplicitSender
    with AnyWordSpecLike
    with Matchers
    with BeforeAndAfterAll
    with BeforeAndAfterEach
    with Eventually {

  private val wireMockServer = new WireMockServer(wireMockConfig().port(0))
  wireMockServer.start()
  WireMock.configureFor(wireMockServer.port())

  private val namespace = "namespace-test"
  private val podName1 = "pod-test-1"
  private val podName2 = "pod-test-2"
  private lazy val system2 = ActorSystem(system.name, system.settings.config)

  private def settings(podName: String) = {
    new KubernetesSettings(
      apiCaPath = "",
      apiTokenPath = "",
      apiServiceHost = "localhost",
      apiServicePort = wireMockServer.port(),
      namespace = Some(namespace),
      namespacePath = "",
      podName = podName,
      secure = false,
      apiServiceRequestTimeout = 2.seconds,
      customResourceSettings = new CustomResourceSettings(enabled = false, crName = None, 60.seconds),
      revisionAnnotation = "deployment.kubernetes.io/revision"
    )
  }

  private val kubernetesApi =
    new KubernetesApiImpl(
      system,
      settings(podName1),
      namespace,
      apiToken = "apiToken",
      clientHttpsConnectionContext = None)

  private def annotatorProps(pod: String) =
    PodDeletionCostAnnotator.props(
      settings(pod),
      PodDeletionCostSettings(system.settings.config.getConfig("akka.rollingupdate.kubernetes")),
      kubernetesApi,
      crName = None
    )

  override implicit val patienceConfig: PatienceConfig =
    PatienceConfig(timeout = Span(5, Seconds), interval = Span(100, Millis))

  override protected def afterAll(): Unit = {
    super.shutdown()
    TestKit.shutdownActorSystem(system2)
  }

  override protected def beforeEach(): Unit = {
    wireMockServer.resetAll()
  }

  private def podK8sPath(podName: String) = urlEqualTo("/api/v1/namespaces/" + namespace + "/pods/" + podName)

  private def stubForPods(podName: String, cost: Int = 10000, returnCode: Int) =
    stubFor(patchPodDeletionCost(podName, cost).willReturn(aResponse().withStatus(returnCode)))

  private def patchPodDeletionCost(podName: String, cost: Int = 10000): MappingBuilder =
    patch(podK8sPath(podName))
      .withHeader("Content-Type", new EqualToPattern("application/merge-patch+json"))
      .withRequestBody(new ContainsPattern(
        s"""{"metadata": {"annotations": {"controller.kubernetes.io/pod-deletion-cost": "$cost" }}}"""))

  "The pod-deletion-cost annotator, when under normal behavior" should {

    "have a single node cluster running first" in {
      val probe = TestProbe()
      Cluster(system).join(Cluster(system).selfMember.address)
      probe.awaitAssert({
        Cluster(system).selfMember.status == MemberStatus.Up
      }, 3.seconds)

    }

    "correctly annotate the cluster node" in {
      stubForPods(podName1, returnCode = 200)
      expectLogInfo(pattern = ".*Updating pod-deletion-cost annotation.*") {
        system.actorOf(annotatorProps(podName1))
      }
      eventually {
        verify(1, patchRequestedFor(podK8sPath(podName1)))
      }
    }

    "give up when failing with non-transient error" in {
      stubForPods(podName1, returnCode = 404)
      expectLogError(pattern = ".*Not retrying, check configuration.*") {
        system.actorOf(annotatorProps(podName1))
      }
    }

    "retry when failing with transient error" in {
      val scenarioName = "RetryScenario"

      // first call fails
      stubFor(
        patchPodDeletionCost(podName1)
          .inScenario(scenarioName)
          .whenScenarioStateIs("FAILING")
          .willReturn(aResponse().withStatus(500))
          .willSetStateTo("AVAILABLE"))

      // second call succeeds
      stubFor(
        patchPodDeletionCost(podName1)
          .inScenario(scenarioName)
          .whenScenarioStateIs("AVAILABLE")
          .willReturn(aResponse().withStatus(200))
          .willSetStateTo("OK"))
      wireMockServer.setScenarioState(scenarioName, "FAILING") // set starting state to failing

      assertState(scenarioName, "FAILING")
      system.actorOf(annotatorProps(podName1))
      assertState(scenarioName, "AVAILABLE")
      // after the retry backoff delay
      assertState(scenarioName, "OK")

      wireMockServer.checkForUnmatchedRequests()
    }

    "annotate a second node correctly" in {

      stubForPods(podName2, cost = 9900, returnCode = 200)

      val probe = TestProbe()
      Cluster(system2).join(Cluster(system).selfMember.address)
      probe.awaitAssert({
        Cluster(system2).selfMember.status == MemberStatus.Up
      }, 3.seconds)

      system2.actorOf(annotatorProps(podName2))
      eventually {
        verify(1, patchRequestedFor(podK8sPath(podName2)))
      }

      wireMockServer.checkForUnmatchedRequests()
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
        patchPodDeletionCost(podName1)
          .inScenario(scenarioName)
          .whenScenarioStateIs("FAILING")
          .willReturn(aResponse().withStatus(500)))
      wireMockServer.setScenarioState(scenarioName, "FAILING") // set starting state to failing

      // second call succeeds
      stubFor(
        patchPodDeletionCost(podName1)
          .inScenario(scenarioName)
          .whenScenarioStateIs("AVAILABLE")
          .willReturn(aResponse().withStatus(200))
          .willSetStateTo("OK"))

      assertState(scenarioName, "FAILING")

      val underTest = expectLogWarning(".*Failed to update pod-deletion-cost annotation:.*") {
        system.actorOf(annotatorProps(podName1))
      }

      wireMockServer.resetRequests()
      val dummyNewMember =
        MemberUp(
          Member(
            UniqueAddress(Address("akka", system.name, Cluster(system).selfAddress.host.get, 2553), 2L),
            Cluster(system).selfRoles,
            Cluster(system).selfMember.appVersion
          ).copyUp(upNumber = 2))
      underTest ! dummyNewMember
      underTest ! dummyNewMember
      underTest ! dummyNewMember

      // no other interactions should have occurred while on backoff regardless of updates to the cluster
      verify(0, patchRequestedFor(podK8sPath(podName1)))

      wireMockServer.setScenarioState(scenarioName, "AVAILABLE")

      eventually {
        verify(1, patchRequestedFor(podK8sPath(podName1)))
      }
      assertState(scenarioName, "OK")
    }
  }

  private def assertState(scenarioName: String, state: String) = eventually {
    val scenario = wireMockServer.getAllScenarios.getScenarios.asScala.toList.find(_.getName == scenarioName).get
    scenario.getState should ===(state)
  }
  def expectLogInfo[T](pattern: String = null)(block: => T): T =
    EventFilter.info(pattern = pattern, occurrences = 1).intercept(block)(system)

  def expectLogError[T](pattern: String = null, occurrences: Int = 1)(block: => T): T =
    EventFilter.error(pattern = pattern, occurrences = occurrences).intercept(block)(system)

  def expectLogWarning[T](pattern: String = null, occurrences: Int = 1)(block: => T): T =
    EventFilter.warning(pattern = pattern, occurrences = occurrences).intercept(block)(system)

}
