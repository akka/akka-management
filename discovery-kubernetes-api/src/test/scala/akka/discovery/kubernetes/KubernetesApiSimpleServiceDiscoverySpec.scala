/*
 * Copyright (C) 2017 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.discovery.kubernetes

import akka.discovery.SimpleServiceDiscovery.ResolvedTarget
import org.scalatest.{ Matchers, WordSpec }

import PodList._

class KubernetesApiSimpleServiceDiscoverySpec extends WordSpec with Matchers {
  "targets" should {
    "calculate the correct list of resolved targets" in {
      val podList = PodList(List(Item(Spec(List(Container("akka-cluster-tooling-example",
                  List(Port("akka-remote", 10000), Port("akka-mgmt-http", 10001), Port("http", 10002))))),
            Status(Some("172.17.0.4"))),
          Item(Spec(List(Container("akka-cluster-tooling-example",
                  List(Port("akka-remote", 10000), Port("akka-mgmt-http", 10001), Port("http", 10002))))),
            Status(None))))

      KubernetesApiSimpleServiceDiscovery.targets(podList,
        "akka-mgmt-http") shouldBe List(ResolvedTarget("172.17.0.4", Some(10001)))
    }
  }
}
