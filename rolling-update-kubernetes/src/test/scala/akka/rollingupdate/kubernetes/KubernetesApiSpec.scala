/*
 * Copyright (C) 2017-2025 Lightbend Inc. <https://akka.io>
 */

package akka.rollingupdate.kubernetes

import akka.actor.ActorSystem
import akka.testkit.EventFilter
import akka.testkit.ImplicitSender
import akka.testkit.TestKit
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.{ aResponse, equalTo, get, stubFor, urlEqualTo }
import com.github.tomakehurst.wiremock.client.MappingBuilder
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.github.tomakehurst.wiremock.matching.EqualToPattern
import com.github.tomakehurst.wiremock.stubbing.Scenario
import com.typesafe.config.ConfigFactory
import org.scalatest.concurrent.Eventually
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.Millis
import org.scalatest.time.Seconds
import org.scalatest.time.Span
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatest.BeforeAndAfterAll
import org.scalatest.BeforeAndAfterEach

import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.Future
import scala.concurrent.duration._

object KubernetesApiSpec {
  val config = ConfigFactory.parseString("""
      akka.loggers = ["akka.testkit.TestEventListener"]
      akka.actor.provider = cluster

      akka.remote.artery.canonical.port = 0
      akka.remote.artery.canonical.hostname = 127.0.0.1

      akka.cluster.jmx.multi-mbeans-in-same-jvm = on
      akka.coordinated-shutdown.terminate-actor-system = off
      akka.coordinated-shutdown.run-by-actor-system-terminate = off
      akka.test.filter-leeway = 10s
    """)
}

class KubernetesApiSpec
    extends TestKit(
      ActorSystem(
        "AppVersionRevisionSpec",
        KubernetesApiSpec.config
      ))
    with ImplicitSender
    with AnyWordSpecLike
    with Matchers
    with BeforeAndAfterAll
    with BeforeAndAfterEach
    with Eventually
    with ScalaFutures {

  private val wireMockServer = new WireMockServer(wireMockConfig().port(0))
  wireMockServer.start()
  WireMock.configureFor(wireMockServer.port())

  // for wiremock to provide json
  val mapper = new ObjectMapper()

  private val namespace = "namespace-test"
  private val podName1 = "pod-test-1"

  private def settings(podName: String) = {
    new KubernetesSettings(
      apiCaPath = "",
      apiTokenPath = "",
      apiServiceHost = "localhost",
      apiServicePort = wireMockServer.port(),
      apiTokenTtl = 500.millis,
      namespace = Some(namespace),
      namespacePath = "",
      podName = podName,
      secure = false,
      apiServiceRequestTimeout = 2.seconds,
      customResourceSettings = new CustomResourceSettings(enabled = false, crName = None, 60.seconds),
      revisionAnnotation = "deployment.kubernetes.io/revision",
      insecureTokens = true
    )
  }

  private val kubernetesApi =
    new KubernetesApiImpl(
      system,
      settings(podName1),
      namespace,
      () => Future.successful("apiToken"),
      clientHttpsConnectionContext = None)

  override implicit val patienceConfig: PatienceConfig =
    PatienceConfig(timeout = Span(5, Seconds), interval = Span(100, Millis))

  override protected def afterAll(): Unit = super.shutdown()

  override protected def beforeEach(): Unit = {
    wireMockServer.resetAll()
    WireMock.resetAllScenarios()
  }

  private def podPath(podName: String) =
    urlEqualTo(s"/api/v1/namespaces/$namespace/pods/$podName")

  private def replicaPath(replica: String) =
    urlEqualTo(s"/apis/apps/v1/namespaces/$namespace/replicasets/$replica")

  private def getPod(podName: String): MappingBuilder =
    get(podPath(podName)).withHeader("Content-Type", new EqualToPattern("application/json"))

  private def getReplicaSet(replica: String): MappingBuilder =
    get(replicaPath(replica)).withHeader("Content-Type", new EqualToPattern("application/json"))

  private val defaultPodResponseJson =
    """{
      | "metadata": {
      |   "ownerReferences": [
      |     {"name": "wrong-replicaset-id", "kind": "SomethingElse"},
      |     {"name": "parent-replicaset-id", "kind": "ReplicaSet"}
      |    ]
      |  }
      |}""".stripMargin

  private def replicaResponseJson(revision: String): String =
    s"""{
      | "metadata": {
      |   "annotations": {
      |     "deployment.kubernetes.io/revision": "$revision"
      |   }
      |  }
      |}""".stripMargin

  private val defaultReplicaResponseJson = replicaResponseJson("1")

  private def stubPodResponse(json: String = defaultPodResponseJson, state: String = Scenario.STARTED) =
    stubFor(
      getPod(podName1)
        .willReturn(
          ResponseDefinitionBuilder.okForJson("").withJsonBody(mapper.readTree(json))
        )
        .inScenario("pod")
        .whenScenarioStateIs(state))

  private def stubReplicaResponse(json: String = defaultReplicaResponseJson) =
    stubFor(
      getReplicaSet("parent-replicaset-id")
        .willReturn(
          ResponseDefinitionBuilder.okForJson("").withJsonBody(mapper.readTree(json))
        )
        .inScenario("replica")
        .whenScenarioStateIs(Scenario.STARTED))

  "Read revision from Kubernetes" should {

    "parse pod and replica response to get the revision" in {
      stubPodResponse()
      stubReplicaResponse()

      EventFilter
        .info(pattern = "Reading revision from Kubernetes: akka.cluster.app-version was set to 1", occurrences = 1)
        .intercept {
          kubernetesApi.readRevision().futureValue should be("1")
        }
    }

    "parse pod and replica responses to get the revision from custom annotations" in {
      stubPodResponse()
      stubReplicaResponse(defaultReplicaResponseJson.replaceAllLiterally("deployment.kubernetes.io", "custom.akka.io"))

      val customSettings = {
        val base = settings(podName1)
        new KubernetesSettings(
          apiCaPath = base.apiCaPath,
          apiTokenPath = base.apiTokenPath,
          apiServiceHost = base.apiServiceHost,
          apiServicePort = base.apiServicePort,
          apiTokenTtl = 500.millis,
          namespace = base.namespace,
          namespacePath = base.namespacePath,
          podName = base.podName,
          secure = base.secure,
          apiServiceRequestTimeout = base.apiServiceRequestTimeout,
          customResourceSettings = base.customResourceSettings,
          revisionAnnotation = "custom.akka.io/revision"
        )
      }

      val customKubernetesApi =
        new KubernetesApiImpl(
          system,
          customSettings,
          namespace,
          () => Future.successful("apiToken"),
          clientHttpsConnectionContext = None)

      EventFilter
        .info(pattern = "Reading revision from Kubernetes: akka.cluster.app-version was set to 1", occurrences = 1)
        .intercept {
          customKubernetesApi.readRevision().futureValue should be("1")
        }
    }

    "retry and then fail when pod not found" in {
      stubFor(getPod(podName1).willReturn(aResponse().withStatus(404)))
      EventFilter
        .warning(pattern = ".*Failed to get revision", occurrences = 5)
        .intercept({
          assert(kubernetesApi.readRevision().failed.futureValue.isInstanceOf[ReadRevisionException])
        })
    }

    "retry and then fail when replicaset not found" in {
      stubPodResponse()
      stubFor(getReplicaSet("parent-replicaset-id").willReturn(aResponse().withStatus(404)))
      EventFilter
        .warning(pattern = ".*Failed to get revision", occurrences = 5)
        .intercept({
          assert(kubernetesApi.readRevision().failed.futureValue.isInstanceOf[ReadRevisionException])
        })
    }

    "log if pod json can not be parsed" in {
      stubPodResponse(json = """{ "invalid": "json" }""")
      EventFilter
        .warning(pattern = ".*Error while parsing Pod*")
        .intercept({
          assert(kubernetesApi.readRevision().failed.futureValue.isInstanceOf[ReadRevisionException])
        })
    }

    "log if replica json can not be parsed" in {
      stubPodResponse()
      stubReplicaResponse(json = """{ "invalid": "json" }""")
      EventFilter
        .warning(pattern = ".*Error while parsing Pod*")
        .intercept({
          assert(kubernetesApi.readRevision().failed.futureValue.isInstanceOf[ReadRevisionException])
        })
    }

    "break the loop if consecutive request succeeds" in {
      stubFor(
        getPod(podName1)
          .willReturn(aResponse().withStatus(404))
          .inScenario("pod")
          .whenScenarioStateIs(Scenario.STARTED)
          .willSetStateTo("after first fail")
      )
      stubFor(
        getPod(podName1)
          .willReturn(aResponse().withStatus(404))
          .inScenario("pod")
          .whenScenarioStateIs("after first fail")
          .willSetStateTo("k8s is happy now")
      )
      stubPodResponse(state = "k8s is happy now")
      stubReplicaResponse()
      EventFilter
        .warning(pattern = ".*Try again*", occurrences = 2)
        .intercept({
          kubernetesApi.readRevision().futureValue should be("1")
        })
    }

    "support reloading tokens" in {
      val firstToken = "first-token"
      val firstVersion = "1"
      val secondToken = "second-token"
      val secondVersion = "2"

      val token = new AtomicReference[String](firstToken)
      val api = new KubernetesApiImpl(system, settings(podName1), namespace, () => Future.successful(token.get), None)

      stubPodResponse()

      // Two stubs, if the first token is present, we return the first version, if the second token is presented,
      // we return the second version
      stubFor(
        getReplicaSet("parent-replicaset-id")
          .withHeader("Authorization", equalTo(s"Bearer $firstToken"))
          .willReturn(
            aResponse()
              .withStatus(200)
              .withHeader("Content-Type", "application/json")
              .withBody(replicaResponseJson(firstVersion))))
      stubFor(
        getReplicaSet("parent-replicaset-id")
          .withHeader("Authorization", equalTo(s"Bearer $secondToken"))
          .willReturn(
            aResponse()
              .withStatus(200)
              .withHeader("Content-Type", "application/json")
              .withBody(replicaResponseJson(secondVersion))))

      api.readRevision().futureValue should ===(firstVersion)
      // Update the token
      token.set("second-token")
      // If we do a read immediately now, it should still use the first token, since it should be cached because we've
      // configured the cache to be for 500ms
      api.readRevision().futureValue should ===(firstVersion)
      // Now wait 600ms, and it should use the second token
      Thread.sleep(600)
      api.readRevision().futureValue should ===(secondVersion)
    }

  }
}
