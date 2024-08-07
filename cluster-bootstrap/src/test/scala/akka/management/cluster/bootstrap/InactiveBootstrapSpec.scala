/*
 * Copyright (C) 2017-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.management.cluster.bootstrap

import akka.actor.ActorSystem
import akka.management.scaladsl.AkkaManagement

class InactiveBootstrapSpec extends AbstractBootstrapSpec {
  val system = ActorSystem("InactiveBootstrapSpec")

  "cluster-bootstrap on the classpath" should {
    "not fail management routes if bootstrap is not configured or used" in {
      // this will call ClusterBootstrap(system) which should not fail even if discovery is not configured
      AkkaManagement(system)
    }
  }

  override protected def afterAll(): Unit = system.terminate()
}
