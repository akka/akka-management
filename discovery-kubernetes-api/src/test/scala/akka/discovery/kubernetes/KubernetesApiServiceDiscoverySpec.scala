/*
 * Copyright (C) 2017-2018 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.discovery.kubernetes

import java.net.InetAddress

import org.scalatest.{Matchers, WordSpec}
import PodList.{Pod, _}
import akka.actor.ActorSystem
import akka.discovery.Discovery
import akka.discovery.ServiceDiscovery.ResolvedTarget

class KubernetesApiServiceDiscoverySpec extends WordSpec with Matchers {
  "targets" should {
    "calculate the correct list of resolved targets" in {
      val podList =
        PodList(List(Pod(Some(PodSpec(List(Container("akka-cluster-tooling-example",
                      Some(List(ContainerPort(Some("akka-remote"), 10000), ContainerPort(Some("management"), 10001),
                          ContainerPort(Some("http"), 10002))))))),
              Some(PodStatus(Some("172.17.0.4"), Some("Running"))), Some(Metadata(deletionTimestamp = None))),
            Pod(Some(PodSpec(List(Container("akka-cluster-tooling-example",
                      Some(List(ContainerPort(Some("akka-remote"), 10000), ContainerPort(Some("management"), 10001),
                          ContainerPort(Some("http"), 10002))))))), Some(PodStatus(None, Some("Running"))),
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
                          ContainerPort(Some("http"), 10002))))))),
              Some(PodStatus(Some("172.17.0.4"), Some("Running"))),
              Some(Metadata(deletionTimestamp = Some("2017-12-06T16:30:22Z"))))))

      KubernetesApiServiceDiscovery.targets(podList, Some("management"), "default",
        "cluster.local") shouldBe List.empty
    }

    // This test allows users to not declare the management port in their container spec,
    // which is not only convenient, it also is required in Istio where ports declared
    // in the container spec are redirected through Envoy, and in Knative, where only
    // one port is allowed to be declared at all (that port being the primary port for
    // the http/grpc service, not the management or remoting ports).
    "return a single result per host with no port when no port name is requested" in {
      val podList =
        PodList(List(
          // Pod with multiple ports
          Pod(Some(PodSpec(List(Container("akka-cluster-tooling-example",
            Some(List(ContainerPort(Some("akka-remote"), 10000), ContainerPort(Some("management"), 10001),
              ContainerPort(Some("http"), 10002))))))),
            Some(PodStatus(Some("172.17.0.4"), Some("Running"))), Some(Metadata(deletionTimestamp = None))),
          // Pod with no ports
          Pod(Some(PodSpec(List(Container("akka-cluster-tooling-example", None)))),
            Some(PodStatus(Some("172.17.0.5"), Some("Running"))), Some(Metadata(deletionTimestamp = None))),
          // Pod with multiple containers
          Pod(Some(PodSpec(List(
            Container("akka-cluster-tooling-example", Some(List(ContainerPort(Some("akka-remote"), 10000),
              ContainerPort(Some("management"), 10001)))),
            Container("sidecar", Some(List(ContainerPort(Some("http"), 10002))))
          ))), Some(PodStatus(Some("172.17.0.6"), Some("Running"))), Some(Metadata(deletionTimestamp = None)))
        ))

      KubernetesApiServiceDiscovery.targets(podList, None, "default", "cluster.local") shouldBe List(
        ResolvedTarget(
          host = "172-17-0-4.default.pod.cluster.local",
          port = None,
          address = Some(InetAddress.getByName("172.17.0.4"))
        ),
        ResolvedTarget(
          host = "172-17-0-5.default.pod.cluster.local",
          port = None,
          address = Some(InetAddress.getByName("172.17.0.5"))
        ),
        ResolvedTarget(
          host = "172-17-0-6.default.pod.cluster.local",
          port = None,
          address = Some(InetAddress.getByName("172.17.0.6"))
        )
      )
    }

    "ignore non-running pods" in {
      val podList =
        PodList(List(Pod(Some(PodSpec(List(Container("akka-cluster-tooling-example",
                      Some(List(ContainerPort(Some("akka-remote"), 10000), ContainerPort(Some("management"), 10001),
                          ContainerPort(Some("http"), 10002))))))),
              Some(PodStatus(Some("172.17.0.4"), Some("Succeeded"))), Some(Metadata(deletionTimestamp = None)))))

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
