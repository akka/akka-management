/*
 * Copyright (C) 2017-2018 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.discovery.kubernetes

import java.net.InetAddress

import org.scalatest.{ Matchers, WordSpec }
import PodList._
import akka.actor.ActorSystem
import akka.discovery.Discovery
import akka.discovery.ServiceDiscovery.ResolvedTarget

class KubernetesApiServiceDiscoverySpec extends WordSpec with Matchers {
  "targets" should {
    "calculate the correct list of resolved targets" in {
      val podList =
        PodList(List(Pod(Some(PodSpec(List(Container("akka-cluster-tooling-example",
                      Some(List(ContainerPort(Some("akka-remote"), 10000), ContainerPort(Some("management"), 10001),
                          ContainerPort(Some("http"), 10002))))))), Some(PodStatus(Some("172.17.0.4"))),
              Some(Metadata(deletionTimestamp = None))),
            Pod(Some(PodSpec(List(Container("akka-cluster-tooling-example",
                      Some(List(ContainerPort(Some("akka-remote"), 10000), ContainerPort(Some("management"), 10001),
                          ContainerPort(Some("http"), 10002))))))), Some(PodStatus(None)),
              Some(Metadata(deletionTimestamp = None)))))

      KubernetesApiServiceDiscovery.targets(podList, Some("management"), "default", "cluster.local") shouldBe List(
          ResolvedTarget(
            host = "172-17-0-4.default.pod.cluster.local",
            port = Some(10001),
            address = Some(InetAddress.getByName("172.17.0.4"))
          ))
    }

    "ignore deleted pods" in {
      val podList =
        PodList(List(Pod(Some(PodSpec(List(Container("akka-cluster-tooling-example",
                      Some(List(ContainerPort(Some("akka-remote"), 10000), ContainerPort(Some("management"), 10001),
                          ContainerPort(Some("http"), 10002))))))), Some(PodStatus(Some("172.17.0.4"))),
              Some(Metadata(deletionTimestamp = Some("2017-12-06T16:30:22Z"))))))

      KubernetesApiServiceDiscovery.targets(podList, Some("management"), "default",
        "cluster.local") shouldBe List.empty
    }
  }

  "The discovery loading mechanism" should {
    "allow loading kubernetes-api discovery even if it is not the default" in {
      val system = ActorSystem()
      //#kubernetes-api-discovery
      val discovery = Discovery(system).loadServiceDiscovery("kubernetes-api")
      //#kubernetes-api-discovery
      discovery shouldBe a[KubernetesApiServiceDiscovery]
      system.terminate()
    }
  }
}
