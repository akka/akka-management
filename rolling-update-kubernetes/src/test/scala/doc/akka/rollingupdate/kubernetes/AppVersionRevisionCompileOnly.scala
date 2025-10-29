/*
 * Copyright (C) 2017-2025 Lightbend Inc. <https://akka.io>
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
