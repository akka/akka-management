/*
 * Copyright (C) 2017-2025 Lightbend Inc. <https://akka.io>
 */

package akka.cluster.bootstrap

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.Props

object NoisySingleton {
  def props(): Props = Props(new NoisySingleton)
}

class NoisySingleton extends Actor with ActorLogging {

  override def preStart(): Unit =
    log.info("Noisy singleton started")

  override def postStop(): Unit =
    log.info("Noisy singleton stopped")

  override def receive: Receive = {
    case msg => log.info("Msg: {}", msg)
  }
}
