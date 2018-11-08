/*
 * Copyright (C) 2017 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.discovery

import org.scalatest.{ Matchers, OptionValues, WordSpec }

class LookupSpec extends WordSpec with Matchers with OptionValues {

  // some invalid SRV strings, but valid domain names
  // should result in A/AAAA Lookups
  val invalidSRVs = List(
    "portName.protocol.serviceName.local",
    "serviceName.local"
  )

  // SRV strings with invalid domain names
  val srvWithInvalidDomainNames = List(
    "_portName._protocol.service_name.local",
    "_portName._protocol.servicename,local",
    "_portName._protocol.servicename.local-",
    "_portName._protocol.-servicename.local",
  )
  // some invalid domain names
  val invalidDomainNames = List(
    "_portName.serviceName",
    "_serviceName.local",
    "_serviceName,local",
    "-serviceName.local",
    "serviceName.local-",
  ) ++ srvWithInvalidDomainNames

  "Lookup.parseSrvString" should {

    "generate a SRV Lookup from a SRV String" in {
      val name = "_portName._protocol.serviceName.local"
      val lookup = Lookup.fromString(name)
      lookup.serviceName shouldBe "serviceName.local"
      lookup.portName.value shouldBe "portName"
      lookup.protocol.value shouldBe "protocol"
    }

    "generate a A/AAAA from any non-conforming String  " in {
      invalidSRVs.map { str =>
        withClue(s"parsing '$str'") {
          val lookup = Lookup.fromString(str)
          lookup.serviceName shouldBe str
          lookup.portName shouldBe empty
          lookup.protocol shouldBe empty
        }
      }
    }

    "throw exception if domain part is an invalid domain name" in {
      invalidDomainNames.foreach { str =>
        withClue(s"parsing '$str'") {
          try {
            Lookup.fromString(str)
            fail("should fail with exception")
          } catch {
            case _:IllegalArgumentException => // ass expected
          }
        }
      }
    }
  }

  "Lookup.isSrvString" should {

    "return true for any conforming SRV String" in {
      Lookup.isValidSrv("_portName._protocol.serviceName.local") shouldBe true
    }

    "return false for any non-conforming SRV String" in {
      invalidSRVs.map { str =>
        withClue(s"checking '$str'") {
          Lookup.isValidSrv(str) shouldBe false
        }
      }
    }

    "return false for if domain part is an invalid domain name" in {
      srvWithInvalidDomainNames.map { str =>
        withClue(s"checking '$str'") {
          Lookup.isValidSrv(str) shouldBe false
        }
      }
    }

  }
}
