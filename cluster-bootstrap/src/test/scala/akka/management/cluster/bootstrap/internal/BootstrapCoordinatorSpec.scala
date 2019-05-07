/*
 * Copyright (C) 2017-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.management.cluster.bootstrap.internal

import java.util.concurrent.atomic.AtomicReference

import akka.actor.{ ActorRef, ActorSystem, Props }
import akka.discovery.ServiceDiscovery.{ Resolved, ResolvedTarget }
import akka.discovery.{ Lookup, MockDiscovery }
import akka.management.cluster.bootstrap.internal.BootstrapCoordinator.Protocol.InitiateBootstrapping
import akka.management.cluster.bootstrap.{ ClusterBootstrapSettings, LowestAddressJoinDecider }
import com.typesafe.config.ConfigFactory
import org.scalatest.concurrent.Eventually
import org.scalatest.{ BeforeAndAfterAll, Matchers, WordSpec }

import scala.concurrent.{ Await, Future }
import scala.concurrent.duration._

class BootstrapCoordinatorSpec extends WordSpec with Matchers with BeforeAndAfterAll with Eventually {
  val serviceName = "bootstrap-coordinator-test-service"
  val system = ActorSystem("test", ConfigFactory.parseString(s"""
      |akka.management.cluster.bootstrap {
      | contact-point-discovery.service-name = $serviceName
      |}
    """.stripMargin).withFallback(ConfigFactory.load()))
  val settings = ClusterBootstrapSettings(system.settings.config, system.log)
  val joinDecider = new LowestAddressJoinDecider(system, settings)

  val discovery = new MockDiscovery(system)

  MockDiscovery.set(
    Lookup(serviceName, portName = None, protocol = Some("tcp")),
    () =>
      Future.successful(Resolved(serviceName,
          List(
            ResolvedTarget("host1", Some(2552), None),
            ResolvedTarget("host1", Some(8558), None),
            ResolvedTarget("host2", Some(2552), None),
            ResolvedTarget("host2", Some(8558), None)
          )))
  )

  "The bootstrap coordinator, when avoiding named port lookups" should {

    "probe only on the Akka Management port" in {
      val targets = new AtomicReference[List[ResolvedTarget]](Nil)
      val coordinator = system.actorOf(Props(new BootstrapCoordinator(discovery, joinDecider, settings) {
        override def ensureProbing(contactPoint: ResolvedTarget): Option[ActorRef] = {
          println(s"Resolving $contactPoint")
          val targetsSoFar = targets.get
          targets.compareAndSet(targetsSoFar, contactPoint +: targetsSoFar)
          None
        }
      }))
      coordinator ! InitiateBootstrapping
      eventually {
        val targetsToCheck = targets.get
        targetsToCheck.length should be >= (2)
        targetsToCheck.map(_.host) should contain("host1")
        targetsToCheck.map(_.host) should contain("host2")
        targetsToCheck.flatMap(_.port).toSet should be(Set(8558))
      }
    }
  }

  override def afterAll(): Unit = {
    Await.result(system.terminate(), 10.seconds)
    super.afterAll()
  }
}
