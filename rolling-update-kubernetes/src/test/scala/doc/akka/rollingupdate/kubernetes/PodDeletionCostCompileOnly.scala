/*
 * Copyright (C) 2017-2023 Lightbend Inc. <https://www.lightbend.com>
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
