/*
 * Copyright (C) 2017-2023 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.coordination.lease.kubernetes

import scala.concurrent.duration._
import akka.Done
import akka.actor.ActorSystem
import akka.coordination.lease.kubernetes.internal.KubernetesApiImpl
import akka.http.scaladsl.model.StatusCodes
import akka.testkit.TestKit
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.typesafe.config.ConfigFactory
import org.scalatest.BeforeAndAfterAll
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

class KubernetesApiSpec
    extends TestKit(
      ActorSystem(
        "KubernetesApiSpec",
        ConfigFactory.parseString("""akka.coordination.lease.kubernetes {
        |    lease-operation-timeout = 10s
        |}
        |""".stripMargin)
      ))
    with ScalaFutures
    with AnyWordSpecLike
    with BeforeAndAfterAll
    with Matchers
    with BeforeAndAfterEach {

  val wireMockServer = new WireMockServer(wireMockConfig().port(0))
  wireMockServer.start()

  val settings = new KubernetesSettings(
    "",
    "",
    "localhost",
    wireMockServer.port(),
    namespace = Some("lease"),
    "",
    apiServerRequestTimeout = 800.millis,
    false)

  WireMock.configureFor(settings.apiServerPort)

  implicit val patience: PatienceConfig = PatienceConfig(testKitSettings.DefaultTimeout.duration)

  val underTest = new KubernetesApiImpl(system, settings, "lease", "token", None)
  val leaseName = "lease-1"
  val client1 = "client-1"
  val client2 = "client-2"

  override protected def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  override protected def beforeEach(): Unit = {
    wireMockServer.resetAll()
  }

  "Kubernetes lease resource" should {
    "be able to be created" in {
      val version = "1234"
      stubFor(
        post(urlEqualTo("/apis/akka.io/v1/namespaces/lease/leases/lease-1"))
          .willReturn(aResponse().withStatus(201).withHeader("Content-Type", "application/json").withBody(s"""
               |{
               |    "apiVersion": "akka.io/v1",
               |    "kind": "Lease",
               |    "metadata": {
               |        "name": "lease-1",
               |        "namespace": "akka-lease-tests",
               |        "resourceVersion": "$version",
               |        "selfLink": "/apis/akka.io/v1/namespaces/akka-lease-tests/leases/lease-1",
               |        "uid": "c369949e-296c-11e9-9c62-16f8dd5735ba"
               |    },
               |    "spec": {
               |        "owner": "",
               |        "time": 1549439255948
               |    }
               |}
            """.stripMargin)))

      underTest.removeLease(leaseName).futureValue shouldEqual Done
      val leaseRecord = underTest.readOrCreateLeaseResource(leaseName).futureValue
      leaseRecord.owner shouldEqual None
      leaseRecord.version shouldNot equal("")
      leaseRecord.version shouldEqual version
    }

    "update a lease successfully" in {
      val owner = "client1"
      val lease = "lease-1"
      val version = "2"
      val updatedVersion = "3"
      val timestamp = System.currentTimeMillis()
      stubFor(
        put(urlEqualTo(s"/apis/akka.io/v1/namespaces/lease/leases/$lease"))
          .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(s"""
               |{
               |    "apiVersion": "akka.io/v1",
               |    "kind": "Lease",
               |    "metadata": {
               |        "name": "lease-1",
               |        "namespace": "akka-lease-tests",
               |        "resourceVersion": "$updatedVersion",
               |        "selfLink": "/apis/akka.io/v1/namespaces/akka-lease-tests/leases/$lease",
               |        "uid": "c369949e-296c-11e9-9c62-16f8dd5735ba"
               |    },
               |    "spec": {
               |        "owner": "$owner",
               |        "time": $timestamp
               |    }
               |}
            """.stripMargin)))

      val response = underTest.updateLeaseResource(lease, owner, version, timestamp).futureValue
      response shouldEqual Right(LeaseResource(Some(owner), updatedVersion, timestamp))
    }

    "update a lease conflict" in {
      val owner = "client1"
      val conflictedOwner = "client2"
      val lease = "lease-1"
      val version = "2"
      val updatedVersion = "3"
      val timestamp = System.currentTimeMillis()
      // Conflict
      stubFor(
        put(urlEqualTo(s"/apis/akka.io/v1/namespaces/lease/leases/$lease"))
          .willReturn(aResponse().withStatus(StatusCodes.Conflict.intValue)))

      // Read to get version
      stubFor(
        get(urlEqualTo(s"/apis/akka.io/v1/namespaces/lease/leases/$lease")).willReturn(
          aResponse().withStatus(StatusCodes.OK.intValue).withHeader("Content-Type", "application/json").withBody(s"""
               |{
               |    "apiVersion": "akka.io/v1",
               |    "kind": "Lease",
               |    "metadata": {
               |        "name": "lease-1",
               |        "namespace": "akka-lease-tests",
               |        "resourceVersion": "$updatedVersion",
               |        "selfLink": "/apis/akka.io/v1/namespaces/akka-lease-tests/leases/$lease",
               |        "uid": "c369949e-296c-11e9-9c62-16f8dd5735ba"
               |    },
               |    "spec": {
               |        "owner": "$conflictedOwner",
               |        "time": $timestamp
               |    }
               |}
            """.stripMargin)))

      val response = underTest.updateLeaseResource(lease, owner, version, timestamp).futureValue
      response shouldEqual Left(LeaseResource(Some(conflictedOwner), updatedVersion, timestamp))
    }

    "remove lease via DELETE" in {
      val lease = "lease-1"
      stubFor(
        delete(urlEqualTo(s"/apis/akka.io/v1/namespaces/lease/leases/$lease"))
          .willReturn(aResponse().withStatus(StatusCodes.OK.intValue)))

      val response = underTest.removeLease(lease).futureValue
      response shouldEqual Done
    }

    "timeout on readLease" in {
      val owner = "client1"
      val lease = "lease-1"
      val version = "2"
      val timestamp = System.currentTimeMillis()

      stubFor(
        get(urlEqualTo(s"/apis/akka.io/v1/namespaces/lease/leases/$lease")).willReturn(
          aResponse()
            .withFixedDelay((settings.apiServerRequestTimeout * 2).toMillis.toInt) // Oh noes
            .withStatus(StatusCodes.OK.intValue)
            .withHeader("Content-Type", "application/json")
            .withBody(s"""
               |{
               |    "apiVersion": "akka.io/v1",
               |    "kind": "Lease",
               |    "metadata": {
               |        "name": "lease-1",
               |        "namespace": "akka-lease-tests",
               |        "resourceVersion": "$version",
               |        "selfLink": "/apis/akka.io/v1/namespaces/akka-lease-tests/leases/$lease",
               |        "uid": "c369949e-296c-11e9-9c62-16f8dd5735ba"
               |    },
               |    "spec": {
               |        "owner": "$owner",
               |        "time": $timestamp
               |    }
               |}
            """.stripMargin)))

      underTest
        .readOrCreateLeaseResource(lease)
        .failed
        .futureValue
        .getMessage shouldEqual s"Timed out reading lease $lease. Is the API server up?"
    }

    "timeout on create lease" in {
      val lease = "lease-1"

      stubFor(
        get(urlEqualTo(s"/apis/akka.io/v1/namespaces/lease/leases/$lease"))
          .willReturn(aResponse().withStatus(StatusCodes.NotFound.intValue)))

      stubFor(
        post(urlEqualTo(s"/apis/akka.io/v1/namespaces/lease/leases/$lease")).willReturn(
          aResponse()
            .withFixedDelay((settings.apiServerRequestTimeout * 2).toMillis.toInt) // Oh noes
            .withStatus(StatusCodes.OK.intValue)
            .withHeader("Content-Type", "application/json")))

      underTest
        .readOrCreateLeaseResource(lease)
        .failed
        .futureValue
        .getMessage shouldEqual s"Timed out creating lease $lease. Is the API server up?"
    }

    "timeout on updating lease" in {
      val lease = "lease-1"
      val owner = "client"
      stubFor(
        put(urlEqualTo(s"/apis/akka.io/v1/namespaces/lease/leases/$lease")).willReturn(
          aResponse()
            .withFixedDelay((settings.apiServerRequestTimeout * 2).toMillis.toInt) // Oh noes
            .withStatus(StatusCodes.OK.intValue)
            .withHeader("Content-Type", "application/json")))

      underTest.updateLeaseResource(lease, owner, "1").failed.futureValue.getMessage shouldEqual
      s"Timed out updating lease [$lease] to owner [$owner]. It is not known if the update happened. Is the API server up?"
    }

    "timeout on remove lease " in {
      val lease = "lease-1"
      stubFor(
        delete(urlEqualTo(s"/apis/akka.io/v1/namespaces/lease/leases/$lease")).willReturn(
          aResponse()
            .withFixedDelay((settings.apiServerRequestTimeout * 2).toMillis.toInt) // Oh noes
            .withStatus(StatusCodes.OK.intValue)))

      underTest.removeLease(lease).failed.futureValue.getMessage shouldEqual
      s"Timed out removing lease [$lease]. It is not known if the remove happened. Is the API server up?"
    }
  }

}
