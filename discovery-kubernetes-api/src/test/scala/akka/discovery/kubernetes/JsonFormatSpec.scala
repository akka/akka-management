/*
 * Copyright (C) 2017 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.discovery.kubernetes

import org.scalatest.{Matchers, WordSpec}
import spray.json._
import scala.io.Source

import PodList._

class JsonFormatSpec extends WordSpec with Matchers {
  "JsonFormat" should {
    val data = resourceAsString("pods.json")

    "work" in {
      JsonFormat
        .podListFormat
        .read(data.parseJson) shouldBe PodList(
          List(
            Pod(
              Some(
                PodSpec(
                  List(
                    Container(
                      "akka-cluster-tooling-example",
                      Some(List(ContainerPort(Some("akka-remote"), 10000), ContainerPort(Some("akka-mgmt-http"), 10001), ContainerPort(Some("http"), 10002))))))),
                Some(PodStatus(Some("172.17.0.4"))), Some(Metadata(deletionTimestamp = None))),

            Pod(
              Some(
                PodSpec(
                  List(
                    Container(
                      "akka-cluster-tooling-example",
                      Some(List(ContainerPort(Some("akka-remote"), 10000), ContainerPort(Some("akka-mgmt-http"), 10001), ContainerPort(Some("http"), 10002))))))),
                Some(PodStatus(Some("172.17.0.6"))), Some(Metadata(deletionTimestamp = None))),

            Pod(
              Some(
                PodSpec(
                  List(
                    Container(
                      "akka-cluster-tooling-example",
                      Some(List(ContainerPort(Some("akka-remote"), 10000), ContainerPort(Some("akka-mgmt-http"), 10001), ContainerPort(Some("http"), 10002))))))),
                  Some(PodStatus(Some("172.17.0.7"))), Some(Metadata(deletionTimestamp = Some("2017-12-06T16:30:22Z"))))))
    }
  }


  private def resourceAsString(name: String): String =
    Source
      .fromInputStream(getClass.getClassLoader.getResourceAsStream(name))
      .mkString
}
