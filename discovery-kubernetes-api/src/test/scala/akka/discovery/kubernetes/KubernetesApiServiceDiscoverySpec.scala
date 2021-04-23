/*
 * Copyright (C) 2017-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.discovery.kubernetes

import java.net.InetAddress
import PodList.{Pod, _}
import akka.actor.ActorSystem
import akka.discovery.Discovery
import akka.discovery.ServiceDiscovery.ResolvedTarget
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.io.Source

class KubernetesApiServiceDiscoverySpec extends AnyWordSpec with Matchers {
  "targets" should {
    "calculate the correct list of resolved targets" in {
      val podList =
        PodList(
          List(
            Pod(
              Some(PodSpec(List(Container(
                "akka-cluster-tooling-example",
                Some(List(
                  ContainerPort(Some("akka-remote"), 10000),
                  ContainerPort(Some("management"), 10001),
                  ContainerPort(Some("http"), 10002)))
              )))),
              Some(PodStatus(Some("172.17.0.4"), Nil, Some("Running"))),
              Some(Metadata(deletionTimestamp = None))
            ),
            Pod(
              Some(PodSpec(List(Container(
                "akka-cluster-tooling-example",
                Some(List(
                  ContainerPort(Some("akka-remote"), 10000),
                  ContainerPort(Some("management"), 10001),
                  ContainerPort(Some("http"), 10002)))
              )))),
              Some(PodStatus(None, Nil, Some("Running"))),
              Some(Metadata(deletionTimestamp = None))
            )
          ))

      KubernetesApiServiceDiscovery.targets(podList, Some("management"), "default", "cluster.local", false, None) shouldBe List(
        ResolvedTarget(
          host = "172-17-0-4.default.pod.cluster.local",
          port = Some(10001),
          address = Some(InetAddress.getByName("172.17.0.4"))
        ))
    }

    "ignore deleted pods" in {
      val podList =
        PodList(
          List(Pod(
            Some(PodSpec(List(Container(
              "akka-cluster-tooling-example",
              Some(List(
                ContainerPort(Some("akka-remote"), 10000),
                ContainerPort(Some("management"), 10001),
                ContainerPort(Some("http"), 10002)))
            )))),
            Some(PodStatus(Some("172.17.0.4"), Nil, Some("Running"))),
            Some(Metadata(deletionTimestamp = Some("2017-12-06T16:30:22Z")))
          )))

      KubernetesApiServiceDiscovery.targets(podList, Some("management"), "default", "cluster.local", false, None) shouldBe List.empty
    }

    // This test allows users to not declare the management port in their container spec,
    // which is not only convenient, it also is required in Istio where ports declared
    // in the container spec are redirected through Envoy, and in Knative, where only
    // one port is allowed to be declared at all (that port being the primary port for
    // the http/grpc service, not the management or remoting ports).
    "return a single result per host with no port when no port name is requested" in {
      val podList =
        PodList(
          List(
            // Pod with multiple ports
            Pod(
              Some(PodSpec(List(Container(
                "akka-cluster-tooling-example",
                Some(List(
                  ContainerPort(Some("akka-remote"), 10000),
                  ContainerPort(Some("management"), 10001),
                  ContainerPort(Some("http"), 10002)))
              )))),
              Some(PodStatus(Some("172.17.0.4"), Nil, Some("Running"))),
              Some(Metadata(deletionTimestamp = None))
            ),
            // Pod with no ports
            Pod(
              Some(PodSpec(List(Container("akka-cluster-tooling-example", None)))),
              Some(PodStatus(Some("172.17.0.5"), Nil, Some("Running"))),
              Some(Metadata(deletionTimestamp = None))
            ),
            // Pod with multiple containers
            Pod(
              Some(PodSpec(List(
                Container(
                  "akka-cluster-tooling-example",
                  Some(List(ContainerPort(Some("akka-remote"), 10000), ContainerPort(Some("management"), 10001)))),
                Container("sidecar", Some(List(ContainerPort(Some("http"), 10002))))
              ))),
              Some(PodStatus(Some("172.17.0.6"), Nil, Some("Running"))),
              Some(Metadata(deletionTimestamp = None))
            )
          ))

      KubernetesApiServiceDiscovery.targets(podList, None, "default", "cluster.local", false, None) shouldBe List(
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
      val containerName = "cloudstate-sidecar"
      val podList =
        PodList(
          List(Pod(
            Some(PodSpec(List(Container(
              "akka-cluster-tooling-example",
              Some(List(
                ContainerPort(Some("akka-remote"), 10000),
                ContainerPort(Some("management"), 10001),
                ContainerPort(Some("http"), 10002)))
            )))),
            Some(PodStatus(Some("172.17.0.4"), Nil, Some("Succeeded"))),
            Some(Metadata(deletionTimestamp = None))
          )))

      KubernetesApiServiceDiscovery.targets(podList, Some("management"), "default", "cluster.local", false, None) shouldBe List.empty
    }

    "ignore running pods where the Akka container is waiting" in {
      val podList = {
        val data = resourceAsString("multi-container-pod.json")
        import spray.json._
        JsonFormat.podListFormat.read(data.parseJson)
      }

      KubernetesApiServiceDiscovery.targets(podList, None, "b58dbc88-3651-4fb4-8408-60c375592d1d", "cluster.local", false, Some("container-sidecar")) shouldBe List.empty
      // Nonsense for this example data, but to check we do find the other containers:
      KubernetesApiServiceDiscovery.targets(podList, None, "b58dbc88-3651-4fb4-8408-60c375592d1d", "cluster.local", false, Some("user-function")) shouldBe List(
        ResolvedTarget("10-8-7-9.b58dbc88-3651-4fb4-8408-60c375592d1d.pod.cluster.local",None,Some(InetAddress.getByName("10.8.7.9")))
      )
    }

    "use a ip instead of the host if requested" in {
      val podList =
        PodList(
          List(
            Pod(
              Some(PodSpec(List(Container(
                "akka-cluster-tooling-example",
                Some(List(
                  ContainerPort(Some("akka-remote"), 10000),
                  ContainerPort(Some("management"), 10001),
                  ContainerPort(Some("http"), 10002)))
              )))),
              Some(PodStatus(Some("172.17.0.4"), Nil, Some("Running"))),
              Some(Metadata(deletionTimestamp = None))
            )
          ))

      KubernetesApiServiceDiscovery.targets(podList, Some("management"), "default", "cluster.local", true, None) shouldBe List(
        ResolvedTarget(
          host = "172.17.0.4",
          port = Some(10001),
          address = Some(InetAddress.getByName("172.17.0.4"))
        ))
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

  private def resourceAsString(name: String): String =
    Source.fromInputStream(getClass.getClassLoader.getResourceAsStream(name)).mkString
}
