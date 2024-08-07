/*
 * Copyright (C) 2017-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package doc.akka.rollingupdate.kubernetes

import akka.actor.ActorSystem
import akka.rollingupdate.kubernetes.AppVersionRevision

object AppVersionRevisionCompileOnly {

  val system = ActorSystem()

  //#start
  // Starting the AppVersionRevision extension
  // preferred to be called before ClusterBootstrap
  AppVersionRevision(system).start()
  //#start

}
