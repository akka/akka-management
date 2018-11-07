/*
 * Copyright (C) 2017 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.discovery

import org.scalatest.{ Matchers, OptionValues, WordSpec }

class LookupSpec extends WordSpec with Matchers with OptionValues {

  // some invalid SRV strings
  val invalidSRVs = List(
    "_portName.serviceName",
    "portName.protocol.serviceName",
    "_portName_protocol.serviceName",
    "serviceName"
  )

  "Lookup.parseSrvString" should {
    "generate a SRV Lookup from a SRV String" in {

      val name = "_portName._protocol.serviceName"

      val lookup = Lookup.parseSrvString(name)
      lookup.serviceName shouldBe "serviceName"
      lookup.portName.value shouldBe "portName"
      lookup.protocol.value shouldBe "protocol"
    }

    "generate a A/AAAA from any non-conforming String  " in {
      invalidSRVs.map { str =>
        withClue(s"parsing '$str'") {
          val lookup = Lookup.parseSrvString(str)
          lookup.serviceName shouldBe str
          lookup.portName shouldBe empty
          lookup.protocol shouldBe empty
        }
      }
    }
  }

  "Lookup.isSrvString" should {
    "true for any conforming SRV String" in {
      Lookup.isSrvString("_portName._protocol.serviceName") shouldBe true
    }

    "false for any conforming SRV String" in {
      invalidSRVs.map { str =>
        withClue(s"checking '$str'") {
          Lookup.isSrvString(str) shouldBe false
        }
      }
    }

  }
}
