/*
 * Copyright (C) 2017 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.cluster.bootstrap

import akka.actor.Actor

class NoisySingleton extends Actor {

  override def preStart(): Unit =
    context.system.log.info("Noisy singleton started")

  override def postStop(): Unit =
    context.system.log.info("Noisy singleton stopped")

  override def receive: Receive = {
    case msg => context.system.log.info("Msg: {}", msg)
  }
}
