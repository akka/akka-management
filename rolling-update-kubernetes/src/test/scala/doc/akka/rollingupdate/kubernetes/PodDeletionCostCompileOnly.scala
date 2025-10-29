/*
 * Copyright (C) 2017-2025 Lightbend Inc. <https://akka.io>
 */

package doc.akka.rollingupdate.kubernetes

import akka.actor.ActorSystem
import akka.rollingupdate.kubernetes.PodDeletionCost

object PodDeletionCostCompileOnly {

  val system = ActorSystem()

  //#start
  // Starting the pod deletion cost annotator
  PodDeletionCost(system).start()
  //#start

}
