/*
 * Copyright (C) 2017-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.discovery.azureapi.rbac.aks

import akka.actor.ActorSystem
import akka.discovery.Discovery
import akka.discovery.ServiceDiscovery.ResolvedTarget
import akka.discovery.azureapi.AzureApiSpec
import akka.discovery.azureapi.rbac.aks.PodList._
import com.typesafe.config.{ Config, ConfigFactory }

import java.net.InetAddress

class AzureRbacAksServiceDiscoverySpec extends AzureApiSpec {
  "targets" should {
    "calculate the correct list of resolved targets" in {
      val pods = PodList(
        List(
          Pod(
            Some(PodSpec(List(Container(
              "akka-cluster-tooling-example",
              Some(List(
                ContainerPort(Some("akka-remote"), 10000),
                ContainerPort(Some("management"), 10001),
                ContainerPort(Some("http"), 10002)))
            )))),
            Some(PodStatus(Some("172.17.0.4"), Some(Nil), Some("Running"))),
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
            Some(PodStatus(None, Some(Nil), Some("Running"))),
            Some(Metadata(deletionTimestamp = None))
          )
        ))

      AzureRbacAksServiceDiscovery.targets(pods, Some("management"), "default", "cluster.local", false, None) shouldBe List(
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
            Some(PodStatus(Some("172.17.0.4"), Some(Nil), Some("Running"))),
            Some(Metadata(deletionTimestamp = Some("2017-12-06T16:30:22Z")))
          )))

      AzureRbacAksServiceDiscovery.targets(podList, Some("management"), "default", "cluster.local", false, None) shouldBe List.empty
    }

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
              Some(PodStatus(Some("172.17.0.4"), Some(Nil), Some("Running"))),
              Some(Metadata(deletionTimestamp = None))
            ),
            // Pod with no ports
            Pod(
              Some(PodSpec(List(Container("akka-cluster-tooling-example", None)))),
              Some(PodStatus(Some("172.17.0.5"), Some(Nil), Some("Running"))),
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
              Some(PodStatus(Some("172.17.0.6"), Some(Nil), Some("Running"))),
              Some(Metadata(deletionTimestamp = None))
            )
          ))

      AzureRbacAksServiceDiscovery.targets(podList, None, "default", "cluster.local", false, None) shouldBe List(
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
        PodList(
          List(Pod(
            Some(PodSpec(List(Container(
              "akka-cluster-tooling-example",
              Some(List(
                ContainerPort(Some("akka-remote"), 10000),
                ContainerPort(Some("management"), 10001),
                ContainerPort(Some("http"), 10002)))
            )))),
            Some(PodStatus(Some("172.17.0.4"), Some(Nil), Some("Succeeded"))),
            Some(Metadata(deletionTimestamp = None))
          )))

      AzureRbacAksServiceDiscovery.targets(podList, Some("management"), "default", "cluster.local", false, None) shouldBe List.empty
    }

    "ignore running pods where the Akka container is waiting" in {
      val podList = {
        val data = resourceAsString("multi-container-pod.json")
        import spray.json._
        JsonFormat.podListFormat.read(data.parseJson)
      }

      AzureRbacAksServiceDiscovery.targets(
        podList,
        None,
        "b58dbc88-3651-4fb4-8408-60c375592d1d",
        "cluster.local",
        false,
        Some("container-sidecar")) shouldBe List.empty
      // Nonsense for this example data, but to check we do find the other containers:
      AzureRbacAksServiceDiscovery.targets(
        podList,
        None,
        "b58dbc88-3651-4fb4-8408-60c375592d1d",
        "cluster.local",
        false,
        Some("user-function")) shouldBe List(
        ResolvedTarget(
          "10-8-7-9.b58dbc88-3651-4fb4-8408-60c375592d1d.pod.cluster.local",
          None,
          Some(InetAddress.getByName("10.8.7.9")))
      )
    }

    "ignore pending pods" in {
      val podList = {
        val data = resourceAsString("multi-container-pod-pending.json")
        import spray.json._
        JsonFormat.podListFormat.read(data.parseJson)
      }

      podList.items.flatMap(_.status.map(_.containerStatuses)) shouldBe List(None)
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
              Some(PodStatus(Some("172.17.0.4"), Some(Nil), Some("Running"))),
              Some(Metadata(deletionTimestamp = None))
            )
          ))

      AzureRbacAksServiceDiscovery.targets(podList, Some("management"), "default", "cluster.local", true, None) shouldBe List(
        ResolvedTarget(
          host = "172.17.0.4",
          port = Some(10001),
          address = Some(InetAddress.getByName("172.17.0.4"))
        ))
    }
  }

  "The discovery loading mechanism" should {
    "allow loading kubernetes-api discovery even if it is not the default" in {
      System.setProperty("AZURE_TENANT_ID", "mockmock-mock-mock-mock-mockmockmock")
      System.setProperty("AZURE_CLIENT_ID", "mockmock-mock-mock-mock-mockmockmock")
      System.setProperty("KUBERNETES_SERVICE_HOST", "10.1.0.0")
      System.setProperty("KUBERNETES_SERVICE_PORT", "443")
      val config: Config = ConfigFactory.load()

      val system = ActorSystem("default", config)
      //#kubernetes-api-discovery
      val discovery = Discovery(system).loadServiceDiscovery("azure-rbac-aks-api")
      //#kubernetes-api-discovery
      discovery shouldBe a[AzureRbacAksServiceDiscovery]
      system.terminate()
    }
  }
}
