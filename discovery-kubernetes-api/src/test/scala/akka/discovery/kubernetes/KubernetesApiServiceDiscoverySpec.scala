/*
 * Copyright (C) 2017-2018 Lightbend Inc. <http://www.lightbend.com>
 */

package akka.discovery.kubernetes

import java.net.InetAddress

import org.scalatest.{ Matchers, WordSpec }
import PodList._
import akka.discovery.ServiceDiscovery.ResolvedTarget

class KubernetesApiServiceDiscoverySpec extends WordSpec with Matchers {
  "targets" should {
    "calculate the correct list of resolved targets" in {
      val podList =
        PodList(List(Pod(Some(PodSpec(List(Container("akka-cluster-tooling-example",
                      Some(List(ContainerPort(Some("akka-remote"), 10000),
                          ContainerPort(Some("akka-mgmt-http"), 10001), ContainerPort(Some("http"), 10002))))))),
              Some(PodStatus(Some("172.17.0.4"))), Some(Metadata(deletionTimestamp = None))),
            Pod(Some(PodSpec(List(Container("akka-cluster-tooling-example",
                      Some(List(ContainerPort(Some("akka-remote"), 10000),
                          ContainerPort(Some("akka-mgmt-http"), 10001), ContainerPort(Some("http"), 10002))))))),
              Some(PodStatus(None)), Some(Metadata(deletionTimestamp = None)))))

      KubernetesApiServiceDiscovery.targets(podList, "akka-mgmt-http", "default", "cluster.local") shouldBe List(
          ResolvedTarget(
            host = "172-17-0-4.default.pod.cluster.local",
            port = Some(10001),
            address = Some(InetAddress.getByName("172.17.0.4"))
          ))
    }

    "ignore deleted pods" in {
      val podList =
        PodList(List(Pod(Some(PodSpec(List(Container("akka-cluster-tooling-example",
                      Some(List(ContainerPort(Some("akka-remote"), 10000),
                          ContainerPort(Some("akka-mgmt-http"), 10001), ContainerPort(Some("http"), 10002))))))),
              Some(PodStatus(Some("172.17.0.4"))), Some(Metadata(deletionTimestamp = Some("2017-12-06T16:30:22Z"))))))

      KubernetesApiServiceDiscovery.targets(podList, "akka-mgmt-http", "default", "cluster.local") shouldBe List.empty
    }
  }
}
