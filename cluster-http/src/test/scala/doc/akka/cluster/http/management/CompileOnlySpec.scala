/*
 * Copyright (C) 2017-2018 Lightbend Inc. <http://www.lightbend.com>
 */

package doc.akka.cluster.http.management

import akka.actor.ActorSystem
import akka.management.cluster.ClusterHttpManagement

object CompileOnlySpec {

  //#loading
  val system = ActorSystem()
  val httpMgmt = ClusterHttpManagement(system)
  //#loading
}
