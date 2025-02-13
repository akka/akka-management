/*
 * Copyright (C) 2017-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.discovery.kubernetes

import java.net.InetAddress
import PodList.{ Pod, _ }
import akka.actor.ActorSystem
import akka.discovery.Discovery
import akka.discovery.ServiceDiscovery.ResolvedTarget
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.io.Source

class KubernetesApiServiceDiscoverySpec extends AnyWordSpec with Matchers {
  val notReadyConditions = List(PodCondition(Some("Ready"), Some("False")))
  val readyConditions = List(PodCondition(Some("Ready"), Some("True")))

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
              Some(PodStatus(Some("172.17.0.4"), Some(Nil), Some("Running"), Some(notReadyConditions))),
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
              Some(PodStatus(None, Some(Nil), Some("Running"), Some(readyConditions))),
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
              Some(PodStatus(Some("172.17.0.5"), Some(Nil), Some("Running"), Some(readyConditions))),
              Some(Metadata(deletionTimestamp = None))
            )
          ))

      KubernetesApiServiceDiscovery.targets(podList, Some("management"), "default", "cluster.local", false, None, false) shouldBe List(
        ResolvedTarget(
          host = "172-17-0-4.default.pod.cluster.local",
          port = Some(10001),
          address = Some(InetAddress.getByName("172.17.0.4"))
        ),
        ResolvedTarget(
          host = "172-17-0-5.default.pod.cluster.local",
          port = Some(10001),
          address = Some(InetAddress.getByName("172.17.0.5"))
        )
      )

      KubernetesApiServiceDiscovery.targets(podList, Some("management"), "default", "cluster.local", false, None, true) shouldBe List(
        ResolvedTarget(
          host = "172-17-0-5.default.pod.cluster.local",
          port = Some(10001),
          address = Some(InetAddress.getByName("172.17.0.5"))
        ))
    }

    "ignore deleted pods" in {
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
              Some(PodStatus(Some("172.17.0.4"), Some(Nil), Some("Running"), Some(readyConditions))),
              Some(Metadata(deletionTimestamp = Some("2017-12-06T16:30:22Z")))
            ),
            Pod(
              Some(PodSpec(List(Container(
                "akka-cluster-tooling-example",
                Some(List(
                  ContainerPort(Some("akka-remote"), 10000),
                  ContainerPort(Some("management"), 10001),
                  ContainerPort(Some("http"), 10002)))
              )))),
              Some(PodStatus(Some("172.17.0.4"), Some(Nil), Some("Running"), Some(notReadyConditions))),
              Some(Metadata(deletionTimestamp = Some("2017-12-06T16:30:22Z")))
            )
          ))

      KubernetesApiServiceDiscovery.targets(podList, Some("management"), "default", "cluster.local", false, None, false) shouldBe List.empty

      KubernetesApiServiceDiscovery.targets(podList, Some("management"), "default", "cluster.local", false, None, true) shouldBe List.empty
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
            // Ready pod with multiple ports
            Pod(
              Some(PodSpec(List(Container(
                "akka-cluster-tooling-example",
                Some(List(
                  ContainerPort(Some("akka-remote"), 10000),
                  ContainerPort(Some("management"), 10001),
                  ContainerPort(Some("http"), 10002)))
              )))),
              Some(PodStatus(Some("172.17.0.4"), Some(Nil), Some("Running"), Some(readyConditions))),
              Some(Metadata(deletionTimestamp = None))
            ),
            // Ready pod with no ports
            Pod(
              Some(PodSpec(List(Container("akka-cluster-tooling-example", None)))),
              Some(PodStatus(Some("172.17.0.5"), Some(Nil), Some("Running"), Some(readyConditions))),
              Some(Metadata(deletionTimestamp = None))
            ),
            // Ready pod with multiple containers
            Pod(
              Some(PodSpec(List(
                Container(
                  "akka-cluster-tooling-example",
                  Some(List(ContainerPort(Some("akka-remote"), 10000), ContainerPort(Some("management"), 10001)))),
                Container("sidecar", Some(List(ContainerPort(Some("http"), 10002))))
              ))),
              Some(PodStatus(Some("172.17.0.6"), Some(Nil), Some("Running"), Some(readyConditions))),
              Some(Metadata(deletionTimestamp = None))
            ),
            // Unready pod with multiple ports
            Pod(
              Some(PodSpec(List(Container(
                "akka-cluster-tooling-example",
                Some(List(
                  ContainerPort(Some("akka-remote"), 10000),
                  ContainerPort(Some("management"), 10001),
                  ContainerPort(Some("http"), 10002)))
              )))),
              Some(PodStatus(Some("172.17.0.7"), Some(Nil), Some("Running"), Some(notReadyConditions))),
              Some(Metadata(deletionTimestamp = None))
            ),
            // Unready pod with no ports
            Pod(
              Some(PodSpec(List(Container("akka-cluster-tooling-example", None)))),
              Some(PodStatus(Some("172.17.0.8"), Some(Nil), Some("Running"), Some(notReadyConditions))),
              Some(Metadata(deletionTimestamp = None))
            ),
            // Unready pod with multiple containers
            Pod(
              Some(PodSpec(List(
                Container(
                  "akka-cluster-tooling-example",
                  Some(List(ContainerPort(Some("akka-remote"), 10000), ContainerPort(Some("management"), 10001)))),
                Container("sidecar", Some(List(ContainerPort(Some("http"), 10002))))
              ))),
              Some(PodStatus(Some("172.17.0.9"), Some(Nil), Some("Running"), Some(notReadyConditions))),
              Some(Metadata(deletionTimestamp = None))
            )
          ))

      KubernetesApiServiceDiscovery.targets(podList, None, "default", "cluster.local", false, None, false) shouldBe List(
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
        ),
        ResolvedTarget(
          host = "172-17-0-7.default.pod.cluster.local",
          port = None,
          address = Some(InetAddress.getByName("172.17.0.7"))
        ),
        ResolvedTarget(
          host = "172-17-0-8.default.pod.cluster.local",
          port = None,
          address = Some(InetAddress.getByName("172.17.0.8"))
        ),
        ResolvedTarget(
          host = "172-17-0-9.default.pod.cluster.local",
          port = None,
          address = Some(InetAddress.getByName("172.17.0.9"))
        )
      )

      KubernetesApiServiceDiscovery.targets(podList, None, "default", "cluster.local", false, None, true) shouldBe List(
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
            Some(PodStatus(Some("172.17.0.4"), Some(Nil), Some("Succeeded"), Some(readyConditions))),
            Some(Metadata(deletionTimestamp = None))
          )))

      KubernetesApiServiceDiscovery.targets(podList, Some("management"), "default", "cluster.local", false, None, false) shouldBe List.empty

      KubernetesApiServiceDiscovery.targets(podList, Some("management"), "default", "cluster.local", false, None, true) shouldBe List.empty
    }

    "ignore running pods where the Akka container is waiting" in {
      val podList = {
        val data = resourceAsString("multi-container-pod.json")
        import spray.json._
        JsonFormat.podListFormat.read(data.parseJson)
      }

      val podIsReadyList =
        podList.copy(items = podList.items.map { pod =>
          pod.copy(status = pod.status.map { status =>
            status.copy(conditions = status.conditions.map { conditionList =>
              conditionList.map { condition =>
                if (condition.`type`.contains("Ready"))
                  condition.copy(status = Some("True"))
                else condition
              }
            })
          })
        })

      KubernetesApiServiceDiscovery.targets(
        podList,
        None,
        "b58dbc88-3651-4fb4-8408-60c375592d1d",
        "cluster.local",
        false,
        Some("container-sidecar"),
        false) shouldBe List.empty

      // This could actually pass simply because the pod isn't ready
      KubernetesApiServiceDiscovery.targets(
        podList,
        None,
        "b58dbc88-3651-4fb4-8408-60c375592d1d",
        "cluster.local",
        false,
        Some("container-sidecar"),
        true) shouldBe List.empty

      // Since the pod is ready, the empty result is because the Akka container is waiting
      KubernetesApiServiceDiscovery.targets(
        podIsReadyList,
        None,
        "b58dbc88-3651-4fb4-8408-60c375592d1d",
        "cluster.local",
        false,
        Some("container-sidecar"),
        true) shouldBe List.empty

      // Nonsense for this example data, but to check we do find the other containers:
      KubernetesApiServiceDiscovery.targets(
        podList,
        None,
        "b58dbc88-3651-4fb4-8408-60c375592d1d",
        "cluster.local",
        false,
        Some("user-function"),
        false) shouldBe List(
        ResolvedTarget(
          "10-8-7-9.b58dbc88-3651-4fb4-8408-60c375592d1d.pod.cluster.local",
          None,
          Some(InetAddress.getByName("10.8.7.9")))
      )

      // We'd find the other container but the pod itself isn't ready
      KubernetesApiServiceDiscovery.targets(
        podList,
        None,
        "b58dbc88-3651-4fb4-8408-60c375592d1d",
        "cluster.local",
        false,
        Some("user-function"),
        true) should be(empty)

      // We do find the other container in the pod if the pod is ready
      KubernetesApiServiceDiscovery.targets(
        podIsReadyList,
        None,
        "b58dbc88-3651-4fb4-8408-60c375592d1d",
        "cluster.local",
        false,
        Some("user-function"),
        true) shouldBe List(
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
              Some(PodStatus(Some("172.17.0.4"), Some(Nil), Some("Running"), Some(notReadyConditions))),
              Some(Metadata(deletionTimestamp = None))
            )
          ))

      KubernetesApiServiceDiscovery.targets(podList, Some("management"), "default", "cluster.local", true, None, false) shouldBe List(
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

    "allow loading kubernetes-api-for-client discovery even if it is not the default" in {
      val system = ActorSystem()
      //#kubernetes-api-for-client-discovery
      val discovery = Discovery(system).loadServiceDiscovery("kubernetes-api-for-client")
      //#kubernetes-api-for-client-discovery
      discovery shouldBe a[ExternalKubernetesApiServiceDiscovery]
      system.terminate()
    }
  }

  private def resourceAsString(name: String): String =
    Source.fromInputStream(getClass.getClassLoader.getResourceAsStream(name)).mkString
}
