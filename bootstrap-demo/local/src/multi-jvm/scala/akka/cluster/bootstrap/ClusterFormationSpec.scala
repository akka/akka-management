package akka.cluster.bootstrap

import akka.cluster.{Cluster, MemberStatus}
import akka.management.AkkaManagement
import akka.management.cluster.bootstrap.ClusterBootstrap
import akka.remote.testkit.{MultiNodeConfig, MultiNodeSpec}
import akka.testkit.SocketUtil
import com.typesafe.config.ConfigFactory

import scala.concurrent.duration._

object ClusterFormationSpecConfig extends MultiNodeConfig {
  val one = role("one")
  val two = role("two")
  val three = role("three")

  nodeConfig(one)(ConfigFactory.parseString(
    """
     akka.remote.artery.canonical.hostname = "127.0.0.2"
     akka.management.http.hostname = "127.0.0.2"
    """.stripMargin))

 nodeConfig(two)(ConfigFactory.parseString(
    """
     akka.remote.artery.canonical.hostname = "127.0.0.3"
     akka.management.http.hostname = "127.0.0.3"
    """.stripMargin))

 nodeConfig(three)(ConfigFactory.parseString(
    """
     akka.remote.artery.canonical.hostname = "127.0.0.4"
     akka.management.http.hostname = "127.0.0.4"
    """.stripMargin))

  commonConfig(ConfigFactory.parseString(
    """
akka {
  loglevel = INFO

  actor {
    provider = "cluster"
  }

  remote {
    artery {
      enabled = on
      transport = tcp
      canonical.port = 25551
    }
  }
}

akka.discovery {
  method = config
  config.services = {
    local-cluster = {
      endpoints = [
        {
          host = "127.0.0.2"
          port = 8558
        },
        {
          host = "127.0.0.3"
          port = 8558
        },
        {
          host = "127.0.0.4"
          port = 8558
        }
      ]
    }
  }
}

akka.management {
  cluster.bootstrap {
    contact-point-discovery {
      service-name = "local-cluster"
      # Speed up test
      stable-margin = 1 seconds
    }
  }
}
    """))

}

class ClusterFormationSpecMultiJvmNode1 extends ClusterFormationSpec
class ClusterFormationSpecMultiJvmNode2 extends ClusterFormationSpec
class ClusterFormationSpecMultiJvmNode3 extends ClusterFormationSpec

abstract class ClusterFormationSpec extends MultiNodeSpec(ClusterFormationSpecConfig) with STMultiNodeSpec {
  override def initialParticipants: Int = roles.size

  "Cluster formation" must {
    "bootstrap" in {
      AkkaManagement(system).start()
      ClusterBootstrap(system).start()

      awaitAssert({
        Cluster(system).selfMember.status shouldEqual MemberStatus.Up
      }, 10.seconds)
      enterBarrier("done")
    }
  }

}
