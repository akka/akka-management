/*
 * Copyright (C) 2017-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.discovery.kubernetes

import spray.json._
import scala.io.Source

import PodList._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class JsonFormatSpec extends AnyWordSpec with Matchers {
  "JsonFormat" should {
    val data = resourceAsString("pods.json")

    "work" in {
      JsonFormat.podListFormat.read(data.parseJson) shouldBe PodList(
        List(
          Pod(
            Some(PodSpec(List(Container(
              "akka-cluster-tooling-example",
              Some(List(
                ContainerPort(Some("akka-remote"), 10000),
                ContainerPort(Some("management"), 10001),
                ContainerPort(Some("http"), 10002)))
            )))),
            Some(PodStatus(Some("172.17.0.4"), Some("Running"))),
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
            Some(PodStatus(Some("172.17.0.6"), Some("Running"))),
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
            Some(PodStatus(Some("172.17.0.7"), Some("Running"))),
            Some(Metadata(deletionTimestamp = Some("2017-12-06T16:30:22Z")))
          ),
          Pod(
            Some(PodSpec(
              List(Container("akka-cluster-tooling-example", Some(List(ContainerPort(Some("management"), 10001))))))),
            Some(PodStatus(Some("172.17.0.47"), Some("Succeeded"))),
            Some(Metadata(deletionTimestamp = None))
          )
        ))
    }
  }

  private def resourceAsString(name: String): String =
    Source.fromInputStream(getClass.getClassLoader.getResourceAsStream(name)).mkString
}
