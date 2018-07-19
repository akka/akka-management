/*
 * Copyright (C) 2017 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.discovery.local.registration

import java.nio.file.Paths
import java.util.UUID

import org.scalatest.{ BeforeAndAfterAll, Matchers, WordSpec }

class LocalServiceRegistrationSpec extends WordSpec with Matchers with BeforeAndAfterAll {

  private val serviceFile = Paths.get(s"${UUID.randomUUID()}.json")
  val localServiceRegistration = new LocalServiceRegistration(serviceFile)

  "LocalServiceRegistration" should {
    "be empty when file doesn't exist" in {
      localServiceRegistration.localServiceEntries shouldBe empty
    }

    "be empty when file is empty" in {
      require(serviceFile.toFile.createNewFile())
      localServiceRegistration.localServiceEntries shouldBe empty
    }

    "register new services" in {
      localServiceRegistration.add("127.0.0.1", 9999)
      localServiceRegistration.localServiceEntries shouldBe Seq(LocalServiceEntry("127.0.0.1", 9999))
    }

    "registered services" should {
      "be queryable" in {
        localServiceRegistration.isRegistered("127.0.0.1", 9999) shouldBe true
      }

      "be removable" in {
        localServiceRegistration.remove("127.0.0.1", 9999)
        localServiceRegistration.localServiceEntries shouldBe empty
      }
    }

    "removing non-registered services should have no effect" in {
      localServiceRegistration.remove("notregistered", 1234)
      localServiceRegistration.localServiceEntries shouldBe empty
    }

  }

  override protected def afterAll(): Unit =
    serviceFile.toFile.delete()
}
