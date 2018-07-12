/*
 * Copyright (C) 2017 Lightbend Inc. <http://www.lightbend.com>
 */
package doc.akka.discovery

import akka.actor.ActorSystem
import akka.discovery.ServiceDiscovery
import akka.discovery.SimpleServiceDiscovery.{ Full, Simple }

import scala.concurrent.duration._

object CompileOnlySpec {

  //#loading
  val system = ActorSystem()
  val serviceDiscovery = ServiceDiscovery(system).discovery
  //#loading

  //#simple
  serviceDiscovery.lookup(Simple("akka.io"), 1.second)
  // Convenience method for Simple
  serviceDiscovery.lookup("akka.io", 1.second)
  //#simple

  //#full
  serviceDiscovery.lookup(Full("akka.io", "remoting", "tcp"), 1.second)
  //#full

}
