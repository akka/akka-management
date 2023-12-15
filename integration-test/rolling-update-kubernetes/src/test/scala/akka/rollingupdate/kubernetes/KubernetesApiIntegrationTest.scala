/*
 * Copyright (C) 2017-2023 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.rollingupdate.kubernetes

import scala.concurrent.Await
import scala.concurrent.duration._

import akka.Done
import akka.actor.ActorSystem
import akka.cluster.Cluster
import akka.testkit.TestKit
import com.typesafe.config.ConfigFactory
import org.scalatest.concurrent.Eventually
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatest.BeforeAndAfterAll
import org.scalatest.CancelAfterFailure

/**
 * This test requires an API server available on localhost:8080, the PodCost CRD created and a namespace called `rolling`
 *
 * One way of doing this is to have a kubectl proxy open:
 *
 * `kubectl proxy --port=8080`
 *
 */
class KubernetesApiIntegrationTest
    extends TestKit(
      ActorSystem(
        "KubernetesApiIntegrationSpec",
        ConfigFactory.parseString("""
    akka.loglevel = DEBUG
    akka.actor.provider = cluster
    akka.remote.artery.canonical.port = 0
    akka.remote.artery.canonical.hostname = 127.0.0.1
    """)
      ))
    with AnyWordSpecLike
    with Matchers
    with ScalaFutures
    with BeforeAndAfterAll
    with CancelAfterFailure
    with Eventually {

  implicit val patience: PatienceConfig = PatienceConfig(testKitSettings.DefaultTimeout.duration)

  private val cluster = Cluster(system)

  private val settings = new KubernetesSettings(
    "",
    "",
    "localhost",
    8080,
    namespace = Some("rolling"),
    "",
    podName = "pod1",
    secure = false,
    apiServiceRequestTimeout = 1.second,
    new CustomResourceSettings(
      enabled = true,
      crName = None,
      cleanupAfter = 60.seconds
    )
  )

  private val underTest =
    new KubernetesApiImpl(system, settings, settings.namespace.get, apiToken = "", clientHttpsConnectionContext = None)
  private val crName = KubernetesApi.makeDNS1039Compatible(system.name)
  private val podName1 = "pod1"
  private val podName2 = "pod2"
  private var currentVersion = ""

  override protected def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  override protected def beforeAll(): Unit = {
    // do some operation to check the proxy is up
    eventually {
      Await.result(underTest.removePodCostResource(crName), 2.second) shouldEqual Done
    }
  }

  "Kubernetes PodCost resource" should {
    "be able to be created" in {
      val podCostResource = underTest.readOrCreatePodCostResource(crName).futureValue
      podCostResource.version shouldNot equal("")
      podCostResource.version shouldNot equal(null)
      podCostResource.pods shouldEqual Nil
      currentVersion = podCostResource.version
    }

    "be able to read back with same version" in {
      val podCostResource = underTest.readOrCreatePodCostResource(crName).futureValue
      podCostResource.version shouldEqual currentVersion
    }

    "be able to update empty resource" in {
      val podCost = PodCost(
        podName1,
        1,
        cluster.selfUniqueAddress.address.toString,
        cluster.selfUniqueAddress.longUid,
        System.currentTimeMillis())
      val podCostResource = underTest.updatePodCostResource(crName, currentVersion, Vector(podCost)).futureValue
      val success: PodCostResource = podCostResource match {
        case Right(r) => r
        case Left(_)  => fail("There shouldn't be anyone else updating the resource.")
      }
      success.version shouldNot equal(currentVersion)
      currentVersion = success.version
      success.pods shouldEqual Vector(podCost)
    }

    "be able to update a resource if resource version is correct" in {
      val podCost = PodCost(
        podName1,
        2,
        cluster.selfUniqueAddress.address.toString,
        cluster.selfUniqueAddress.longUid,
        System.currentTimeMillis())
      val podCostResource = underTest.updatePodCostResource(crName, currentVersion, Vector(podCost)).futureValue
      val success: PodCostResource = podCostResource match {
        case Right(r) => r
        case Left(_)  => fail("There shouldn't be anyone else updating the resource.")
      }
      success.version shouldNot equal(currentVersion)
      currentVersion = success.version
      success.pods shouldEqual Vector(podCost)
    }

    "not be able to update a resource if resource version is incorrect" in {
      val podCost = PodCost(
        podName1,
        3,
        cluster.selfUniqueAddress.address.toString,
        cluster.selfUniqueAddress.longUid,
        System.currentTimeMillis())
      val podCostResource = underTest.updatePodCostResource(crName, version = "10", Vector(podCost)).futureValue
      val failure: PodCostResource = podCostResource match {
        case Right(_) => fail("Expected update failure (we've used an invalid version!).")
        case Left(r)  => r
      }
      failure.version shouldEqual currentVersion
      currentVersion = failure.version
      failure.pods.head.cost shouldNot equal(podCost.cost)
      failure.pods.head.time shouldNot equal(podCost.time)
    }

    "be able to add more to the resource" in {
      val podCost2 = PodCost(
        podName2,
        4,
        cluster.selfUniqueAddress.address.toString,
        cluster.selfUniqueAddress.longUid,
        System.currentTimeMillis())
      val podCostResource1 = underTest.readOrCreatePodCostResource(crName).futureValue
      val podCostResource2 =
        underTest.updatePodCostResource(crName, currentVersion, podCostResource1.pods :+ podCost2).futureValue
      val success: PodCostResource = podCostResource2 match {
        case Right(r) => r
        case Left(_)  => fail("There shouldn't be anyone else updating the resource.")
      }
      success.version shouldNot equal(currentVersion)
      currentVersion = success.version
      success.pods.last shouldEqual podCost2
      success.pods.size shouldEqual podCostResource1.pods.size + 1
    }
  }

}
