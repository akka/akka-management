/*
 * Copyright (C) 2017-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.discovery.azureapi.rbac.aks

import PodList._
import akka.discovery.azureapi.AzureApiSpec
import spray.json._

class JsonFormatSpec extends AzureApiSpec {
  "JsonFormat" should {
    val pods = resourceAsString("pods.json")

    "work" in {
      JsonFormat.podListFormat.read(pods.parseJson) shouldBe PodList(
        List(
          Pod(
            Some(PodSpec(List(Container("akka-cluster-azure", None)))),
            Some(
              PodStatus(
                Some("10.244.1.225"),
                Some(List(ContainerStatus("akka-cluster-azure", Map("running" -> ())))),
                Some("Running"))),
            Some(
              Metadata(
                deletionTimestamp = None,
                Map(
                  "app" -> "application",
                  "azure.workload.identity/use" -> "true"
                )
              ))
          ),
          Pod(
            Some(PodSpec(List(Container("akka-cluster-azure", None)))),
            Some(
              PodStatus(
                Some("10.244.1.41"),
                Some(List(ContainerStatus("akka-cluster-azure", Map("running" -> ())))),
                Some("Running"))),
            Some(
              Metadata(
                deletionTimestamp = None,
                Map(
                  "app" -> "application",
                  "azure.workload.identity/use" -> "true"
                )
              ))
          ),
          Pod(
            Some(PodSpec(List(Container("akka-cluster-azure", None)))),
            Some(
              PodStatus(
                Some("10.244.1.221"),
                Some(List(ContainerStatus("akka-cluster-azure", Map("running" -> ())))),
                Some("Running"))),
            Some(
              Metadata(
                deletionTimestamp = None,
                Map(
                  "app" -> "application",
                  "azure.workload.identity/use" -> "true"
                )
              ))
          )
        ))
    }
  }
}
