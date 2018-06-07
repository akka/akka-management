/*
 * Copyright (C) 2017 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.discovery.local

import akka.discovery.local.registration.LocalServiceEntry
import org.scalatest.{ Matchers, WordSpec }
import spray.json._
import JsonFormat._

class JsonFormatSpec extends WordSpec with Matchers {

  "A LocalServiceEntry" should {

    "serialize to json" in {
      LocalServiceEntry("127.0.0.1", 8888).toJson shouldBe JsObject("addr" -> JsString("127.0.0.1"),
        "port" -> JsNumber(8888))
    }

    "parsable from string" in {
      """{"addr":"127.0.0.1","port":4554}""".parseJson.convertTo[LocalServiceEntry] shouldBe LocalServiceEntry(
          "127.0.0.1", 4554)
    }

  }

}
