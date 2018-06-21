package akka.discovery.aggregate

import akka.actor.{ActorSystem, ExtendedActorSystem}
import akka.discovery.SimpleServiceDiscovery.{Resolved, ResolvedTarget}
import akka.discovery.{ServiceDiscovery, SimpleServiceDiscovery}
import akka.event.Logging
import akka.testkit.TestKit
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.collection.immutable


class StubbedSimpleServiceDiscovery(system: ExtendedActorSystem) extends SimpleServiceDiscovery {

  override def lookup(name: String, resolveTimeout: FiniteDuration): Future[SimpleServiceDiscovery.Resolved] = {
    if (name == "stubbed") {
      Future.successful(Resolved(name, immutable.Seq(ResolvedTarget("stubbed1", Some(1234)))))
    } else if (name == "fail") {
      Future.failed(new RuntimeException("No resolving for you!"))
    } else {
      Future.successful(Resolved(name, immutable.Seq.empty))
    }
  }
}

object AggregateSimpleServiceDiscoverySpec {
  val config: Config = ConfigFactory.parseString(
    """
      akka {
        loglevel = DEBUG
        discovery {
          method = akka-aggregate

          akka-aggregate {
            discovery-mechanisms = ["stubbed1", "akka-config"]

          }
        }
      }

      akka.discovery.stubbed1 {
        class = akka.discovery.aggregate.StubbedSimpleServiceDiscovery
      }

      akka.discovery.akka-config.services = {
        config1 = {
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
        fail = {
          endpoints = [
            {
              host = "from-config"
            }
          ]
        }
      }
    """)
}

class AggregateSimpleServiceDiscoverySpec
  extends TestKit(ActorSystem("AggregateSimpleDiscoverySpec", AggregateSimpleServiceDiscoverySpec.config))
    with WordSpecLike
    with Matchers
    with BeforeAndAfterAll
    with ScalaFutures {


  override protected def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  val discovery: SimpleServiceDiscovery = ServiceDiscovery(system).discovery

  "Aggregate service discovery" must {

    "only call first one if returns results" in {
      val results = discovery.lookup("stubbed", 100.millis).futureValue
      results shouldEqual Resolved("stubbed", immutable.Seq(ResolvedTarget("stubbed1", Some(1234))))
    }

    "move onto the next if no resolved targets" in {
      val results = discovery.lookup("config1", 100.millis).futureValue
      results shouldEqual Resolved("config1", immutable.Seq(
        ResolvedTarget("cat", Some(1233)), ResolvedTarget("dog", Some(1234)))
      )
    }

    "move onto next if fails" in {
      val results = discovery.lookup("fail", 100.millis).futureValue
      // Stub fails then result comes from config
      results shouldEqual Resolved("fail", immutable.Seq(
        ResolvedTarget("from-config", None))
      )
    }
  }


}
