/*
 * Copyright (C) 2017-2018 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.management.cluster.bootstrap.internal

import akka.actor.{Actor, ActorLogging, Props}
import akka.annotation.InternalApi
import akka.management.cluster.bootstrap.ClusterBootstrapSettings

@InternalApi
private[bootstrap] object RemotingContactPoint {
  case object GetSeedNodes

  val RemotingContactPointActorName = "remotingContactPoint"
  def props(settings: ClusterBootstrapSettings): Props = Props(new RemotingContactPoint(settings))
}

@InternalApi
private[bootstrap] final class RemotingContactPoint(settings: ClusterBootstrapSettings) extends Actor with ActorLogging {

  private val contactPoint = new ContactPoint(context.system, settings, log)

  import RemotingContactPoint.GetSeedNodes

  override def receive: Receive = {
    case GetSeedNodes =>
      sender() ! contactPoint.seedNodes(sender().path.address.toString)
  }
}
