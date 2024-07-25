/*
 * Copyright (C) 2017-2023 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.rollingupdate.kubernetes

import java.util.concurrent.atomic.AtomicInteger

import scala.collection.immutable
import scala.concurrent.Future
import scala.concurrent.duration._

import akka.Done
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
import com.typesafe.config.ConfigFactory
import org.scalatest.BeforeAndAfterAll
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.Millis
import org.scalatest.time.Seconds
import org.scalatest.time.Span
import org.scalatest.wordspec.AnyWordSpecLike

object PodDeletionCostAnnotatorCrSpec {
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

  private[akka] trait TestCallCount {
    val callCount = new AtomicInteger()

    def getCallCount(): Int = callCount.get()
  }

  private[akka] class TestKubernetesApi extends KubernetesApi {
    private var version = 1
    private var podCosts = Vector.empty[PodCost]

    override def namespace: String = "namespace-test"

    override def updatePodDeletionCostAnnotation(podName: String, cost: Int): Future[Done] =
      Future.successful(Done)

    override def readOrCreatePodCostResource(crName: String): Future[PodCostResource] = this.synchronized {
      Future.successful(PodCostResource(version.toString, podCosts))
    }

    override def updatePodCostResource(
        crName: String,
        v: String,
        pods: immutable.Seq[PodCost]): Future[Either[PodCostResource, PodCostResource]] = this.synchronized {

      podCosts = pods.toVector
      version = v.toInt + 1

      Future.successful(Right(PodCostResource(version.toString, podCosts)))
    }

    def getPodCosts(): Vector[PodCost] = this.synchronized {
      podCosts
    }

    override def readRevision(): Future[String] = Future.successful("1")
  }
}

class PodDeletionCostAnnotatorCrSpec
    extends TestKit(
      ActorSystem(
        "PodDeletionCostAnnotatorCrSpec",
        PodDeletionCostAnnotatorCrSpec.config
      ))
    with ImplicitSender
    with AnyWordSpecLike
    with Matchers
    with BeforeAndAfterAll
    with BeforeAndAfterEach
    with Eventually {
  import PodDeletionCostAnnotatorCrSpec._

  private val namespace = "namespace-test"
  private val podName1 = "pod-test-1"
  private val podName2 = "pod-test-2"
  private lazy val system2 = ActorSystem(system.name, system.settings.config)

  private def settings(podName: String) = {
    new KubernetesSettings(
      apiCaPath = "",
      apiTokenPath = "",
      apiServiceHost = "localhost",
      apiServicePort = 0,
      namespace = Some(namespace),
      namespacePath = "",
      podName = podName,
      secure = false,
      apiServiceRequestTimeout = 2.seconds,
      customResourceSettings = new CustomResourceSettings(enabled = false, crName = None, 60.seconds),
      revisionAnnotations = Seq("deployment.kubernetes.io/revision")
    )
  }

  private def annotatorProps(pod: String, kubernetesApi: KubernetesApi) =
    PodDeletionCostAnnotator.props(
      settings(pod),
      PodDeletionCostSettings(system.settings.config.getConfig("akka.rollingupdate.kubernetes")),
      kubernetesApi,
      crName = Some("poddeletioncostannotatorcrspec")
    )

  override implicit val patienceConfig: PatienceConfig =
    PatienceConfig(timeout = Span(5, Seconds), interval = Span(100, Millis))

  override protected def afterAll(): Unit = {
    super.shutdown()
    TestKit.shutdownActorSystem(system2)
  }

  "The pod-deletion-cost annotator, when under normal behavior" should {

    "have a single node cluster running first" in {
      val probe = TestProbe()
      Cluster(system).join(Cluster(system).selfMember.address)
      probe.awaitAssert({
        Cluster(system).selfMember.status == MemberStatus.Up
      }, 3.seconds)
    }

    "correctly add the cluster node to the PodCost resource" in {
      val kubernetesApi = new TestKubernetesApi
      expectLogInfo(pattern = ".*Updating PodCost CR.*") {
        system.actorOf(annotatorProps(podName1, kubernetesApi))
      }
      eventually {
        val podCosts = kubernetesApi.getPodCosts()
        podCosts.size shouldBe 1
        podCosts.head.podName shouldBe podName1
        podCosts.head.cost shouldBe 10000
      }
    }

    "give up when failing with non-transient error" in {
      // e.g. return code 404
      val kubernetesApi = new TestKubernetesApi {
        override def readOrCreatePodCostResource(crName: String): Future[PodCostResource] = {
          Future.failed(new PodCostClientException("test 404"))
        }
      }

      expectLogError(pattern = ".*Not retrying, check configuration.*") {
        system.actorOf(annotatorProps(podName1, kubernetesApi))
      }
    }

    "retry when failing with transient error" in {
      val kubernetesApi = new TestKubernetesApi with TestCallCount {

        override def updatePodCostResource(
            crName: String,
            v: String,
            pods: immutable.Seq[PodCost]): Future[Either[PodCostResource, PodCostResource]] = {
          // first call fails
          if (callCount.incrementAndGet() == 1)
            Future.failed(new PodCostException("test 500"))
          else
            super.updatePodCostResource(crName, v, pods)
        }
      }

      system.actorOf(annotatorProps(podName1, kubernetesApi))
      eventually {
        kubernetesApi.getCallCount() shouldBe 2
        val podCosts = kubernetesApi.getPodCosts()
        podCosts.size shouldBe 1
        podCosts.head.podName shouldBe podName1
        podCosts.head.cost shouldBe 10000
      }
    }

    "retry when conflicting update" in {
      val kubernetesApi = new TestKubernetesApi with TestCallCount {

        override def updatePodCostResource(
            crName: String,
            v: String,
            pods: immutable.Seq[PodCost]): Future[Either[PodCostResource, PodCostResource]] = this.synchronized {
          // conflict for first call
          if (callCount.incrementAndGet() == 1)
            readOrCreatePodCostResource(crName).map { existing =>
              Left(existing)
            }(system.dispatcher)
          else
            super.updatePodCostResource(crName, v, pods)
        }
      }

      system.actorOf(annotatorProps(podName1, kubernetesApi))
      eventually {
        kubernetesApi.getCallCount() shouldBe 2
        val podCosts = kubernetesApi.getPodCosts()
        podCosts.size shouldBe 1
        podCosts.head.podName shouldBe podName1
        podCosts.head.cost shouldBe 10000
      }
    }

    "annotate a second node correctly" in {
      val kubernetesApi = new TestKubernetesApi

      val probe = TestProbe()
      Cluster(system2).join(Cluster(system).selfMember.address)
      probe.awaitAssert({
        Cluster(system2).selfMember.status == MemberStatus.Up
      }, 3.seconds)

      system2.actorOf(annotatorProps(podName2, kubernetesApi))
      eventually {
        val podCosts = kubernetesApi.getPodCosts().sortBy(_.cost)
        podCosts.head.podName shouldBe podName2
        podCosts.head.cost shouldBe 9900
      }
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
      val kubernetesApi = new TestKubernetesApi with TestCallCount {

        override def updatePodCostResource(
            crName: String,
            v: String,
            pods: immutable.Seq[PodCost]): Future[Either[PodCostResource, PodCostResource]] = {
          // first 3 call fails
          if (callCount.incrementAndGet() <= 3)
            Future.failed(new PodCostException("test 500"))
          else
            super.updatePodCostResource(crName, v, pods)
        }
      }

      val underTest = expectLogInfo(".*Failed to update PodCost CR:.*") {
        system.actorOf(annotatorProps(podName1, kubernetesApi))
      }

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
      kubernetesApi.getCallCount() shouldBe 1
      Thread.sleep(100)
      kubernetesApi.getCallCount() shouldBe 1

      eventually {
        kubernetesApi.getCallCount() shouldBe 4
        val podCosts = kubernetesApi.getPodCosts()
        podCosts.size shouldBe 1 // dummyNewMember is not added by pod1
        podCosts.head.podName shouldBe podName1
        podCosts.head.cost shouldBe 10000
      }
    }
  }

  def expectLogInfo[T](pattern: String = null)(block: => T): T =
    EventFilter.info(pattern = pattern, occurrences = 1).intercept(block)(system)

  def expectLogError[T](pattern: String = null, occurrences: Int = 1)(block: => T): T =
    EventFilter.error(pattern = pattern, occurrences = occurrences).intercept(block)(system)

  def expectLogWarning[T](pattern: String = null, occurrences: Int = 1)(block: => T): T =
    EventFilter.warning(pattern = pattern, occurrences = occurrences).intercept(block)(system)

}
