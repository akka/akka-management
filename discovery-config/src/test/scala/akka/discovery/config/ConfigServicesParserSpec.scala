package akka.discovery.config

import akka.discovery.SimpleServiceDiscovery.{Resolved, ResolvedTarget}
import akka.discovery.config.ConfigServicesParserSpec._
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.{Matchers, WordSpec}

import scala.collection.immutable

object ConfigServicesParserSpec {
  val exampleConfig: Config = ConfigFactory.parseString(
    """
      services = {
        service1 = {
          endpoints = [
            {
              host = "cat"
              port = 1233
            },
            {
              host = "dog"
              port = 1234
            }
          ]
        },
        service2 = {
          endpoints = []
        }
      }
    """.stripMargin)
}

class ConfigServicesParserSpec extends WordSpec with Matchers {

  "Config parsing" must {
    "parse" in {
      val config: Config = exampleConfig.getConfig("services")

      val result: Map[String, Resolved] = ConfigServicesParser.parse(config)

      result("service1") shouldEqual Resolved("service1", immutable.Seq(
        ResolvedTarget("cat", Some(1233)),
        ResolvedTarget("dog", Some(1234))
      )
      )
      result("service2") shouldEqual Resolved("service2", immutable.Seq())
    }
  }
}
