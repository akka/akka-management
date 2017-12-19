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
            Item(
              Spec(
                List(
                  Container(
                    "akka-cluster-tooling-example",
                    List(Port("akka-remote", 10000), Port("akka-mgmt-http", 10001), Port("http", 10002))))),
              Status(Some("172.17.0.4"))),

            Item(
              Spec(
                List(
                  Container(
                    "akka-cluster-tooling-example",
                    List(Port("akka-remote", 10000), Port("akka-mgmt-http", 10001), Port("http", 10002))))),
              Status(Some("172.17.0.6"))),

            Item(
              Spec(
                List(
                  Container(
                    "akka-cluster-tooling-example",
                    List(Port("akka-remote", 10000), Port("akka-mgmt-http", 10001), Port("http", 10002))))),
              Status(Some("172.17.0.7")))))
    }
  }

  private def resourceAsString(name: String): String =
    Source
      .fromInputStream(getClass.getClassLoader.getResourceAsStream(name))
      .mkString
}
