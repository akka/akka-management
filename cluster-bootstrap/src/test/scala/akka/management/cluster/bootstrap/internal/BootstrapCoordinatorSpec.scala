/*
 * Copyright (C) 2017-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.management.cluster.bootstrap.internal

import java.net.InetAddress

import akka.actor.{ ActorRef, ActorSystem, Props }
import akka.discovery.ServiceDiscovery.{ Resolved, ResolvedTarget }
import akka.discovery.{ Lookup, ServiceDiscovery }
import akka.http.scaladsl.model.Uri
import akka.management.cluster.bootstrap.LowestAddressJoinDecider
import org.scalatest.{ Matchers, WordSpecLike }
import akka.testkit.{ TestKit, TestProbe }
import akka.management.cluster.bootstrap.ClusterBootstrapSettings
import akka.management.cluster.bootstrap.internal.BootstrapCoordinator.Protocol.InitiateBootstrapping
import com.typesafe.config.ConfigFactory
import org.scalatest.concurrent.ScalaFutures

import scala.concurrent.{ Future, Promise }
import scala.concurrent.duration.FiniteDuration
import scala.collection.immutable

class BootstrapCoordinatorSpec extends TestKit(ActorSystem()) with WordSpecLike with Matchers with ScalaFutures {
  "The BootstrapCoordinator" should {
    "spawn a HttpContactPointBootstrap with the expected base URI" in {
      val settings = new ClusterBootstrapSettings(system.settings.config, system.log)

      val baseUriPromise = Promise[Uri]()

      val staticDiscovery = new ServiceDiscovery {
        override def lookup(lookup: Lookup, resolveTimeout: FiniteDuration): Future[Resolved] = {
          lookup.serviceName should be("default")
          Future.successful(Resolved("default",
              immutable.Seq(ResolvedTarget("foo.example", None, Some(InetAddress.getByName("10.1.2.3"))))))
        }
      }
      val coordinator = system.actorOf(Props(new BootstrapCoordinator(staticDiscovery,
            new LowestAddressJoinDecider(system, settings), settings) {
        override def getOrCreateChild(contactPoint: ServiceDiscovery.ResolvedTarget,
                                      baseUri: Uri,
                                      childActorName: String): ActorRef = {
          baseUriPromise.success(baseUri)
          TestProbe().ref
        }
      }))
      coordinator ! InitiateBootstrapping

      baseUriPromise.future.futureValue should be(Uri("http://foo.example:8558"))
    }

    "spawn a HttpContactPointBootstrap with the expected base URI when connecting by IP address" in {
      val settings = new ClusterBootstrapSettings(
        ConfigFactory
          .parseString("akka.management.cluster.bootstrap.contact-point.connect-by-ip = yes")
          .withFallback(system.settings.config),
        system.log
      )

      val baseUriPromise = Promise[Uri]()

      val staticDiscovery = new ServiceDiscovery {
        override def lookup(lookup: Lookup, resolveTimeout: FiniteDuration): Future[Resolved] = {
          lookup.serviceName should be("default")
          Future.successful(Resolved("default",
              immutable.Seq(ResolvedTarget("foo.example", None, Some(InetAddress.getByName("10.1.2.3"))))))
        }
      }
      val coordinator = system.actorOf(Props(new BootstrapCoordinator(staticDiscovery,
            new LowestAddressJoinDecider(system, settings), settings) {
        override def getOrCreateChild(contactPoint: ServiceDiscovery.ResolvedTarget,
                                      baseUri: Uri,
                                      childActorName: String): ActorRef = {
          baseUriPromise.success(baseUri)
          TestProbe().ref
        }
      }))
      coordinator ! InitiateBootstrapping

      baseUriPromise.future.futureValue should be(Uri("http://10.1.2.3:8558"))
    }
  }
}
