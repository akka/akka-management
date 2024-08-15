package akka.discovery.azureapi.rbac.aks

import akka.discovery.ServiceDiscovery.ResolvedTarget
import akka.discovery.azureapi.AzureApiSpec
import akka.discovery.azureapi.rbac.aks.PodList._

import java.net.InetAddress

class AzureRbacAksServiceDiscoverySpec extends AzureApiSpec {
  "AzureRbacAksServiceDiscovery" should {
    "target should calculate the correct list of resolved targets" in {
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

      AzureRbacAksServiceDiscovery
        .targets(pods, Some("management"), "default", "cluster.local", false, None) shouldBe List(
        ResolvedTarget(
          host = "172-17-0-4.default.pod.cluster.local",
          port = Some(10001),
          address = Some(InetAddress.getByName("172.17.0.4"))
        ))
    }
  }
}
