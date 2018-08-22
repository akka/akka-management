/*
 * Copyright (C) 2017 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.discovery.udp

import java.net.InetSocketAddress

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import akka.pattern.ask
import akka.discovery.Lookup
import akka.discovery.SimpleServiceDiscovery
import akka.discovery.SimpleServiceDiscovery.Resolved
import akka.discovery.SimpleServiceDiscovery.ResolvedTarget
import akka.io.Udp
import akka.util.ByteString
import akka.util.Timeout

import scala.concurrent.Future
import scala.concurrent.duration._

class UdpBroadcastDiscovery(system: ActorSystem) extends SimpleServiceDiscovery {
  val wellKnownPort = 8668 // port number pun on 8558 http port
  val thisServiceName = "my-cluster"
  val thisHostname = system.settings.config.getString("akka.remote.netty.tcp.hostname")
  val thisSystemPort = system.settings.config.getInt("akka.remote.netty.tcp.port")
  val thisManagementHttpPort = system.settings.config.getInt("akka.management.http.port")
  val thisUdpEndpoint = new InetSocketAddress(thisHostname, wellKnownPort)

  case object SendProbe
  case class DoLookup(lookup: Lookup, timeout: Timeout)
  case class ReturnResult(to: ActorRef)

  class UdpHandler extends Actor with ActorLogging {

    import context.dispatcher
    Udp(context.system).manager ! Udp.Bind(self, new InetSocketAddress("0.0.0.0", wellKnownPort),
      Udp.SO.Broadcast(true) :: Nil)
    system.scheduler.schedule(500.millis, 500.millis, self, SendProbe)

    override def receive: Receive = binding

    def binding: Receive = {
      case Udp.Bound(ourAddr) =>
        log.info(s"UDP bound to $ourAddr")
        context.become(probing(sender()))
      case SendProbe => // waiting for binding
    }

    def probing(listener: ActorRef, servicesFound: Set[ResolvedTarget] = Set.empty): Receive = {
      case Udp.Received(data, ret) /*if ret != thisUdpEndpoint*/ =>
        val cmd = data.take(3).utf8String
        val value = data.drop(3).utf8String
        //log.info(s"Received package [$cmd] [$value] from [$ret]")
        if (cmd == "QRY" && value == thisServiceName)
          sender() ! Udp.Send(ByteString(s"RES$thisHostname:$thisManagementHttpPort"), ret)
        else if (cmd == "RES") {
          // TODO: error handling
          val Array(host, port) = value.split(":")
          context.become(probing(listener, servicesFound + ResolvedTarget(host, Some(port.toInt))))
        } else
          log.info(s"Got unexpected probing command: [$cmd] [$value]") // TODO: show more of the message

      /*case r @ Udp.Received(data, ret) =>
        log.info(s"Received unhandled UDP: $r")*/

      case SendProbe =>
        //log.info(s"Broadcasting our address")
        // TODO: we are cheating here, we shouldn't query for `thisServiceName`
        listener ! Udp.Send(ByteString(s"QRY$thisServiceName"), new InetSocketAddress("10.0.0.0", wellKnownPort))

      case DoLookup(lookup, timeout) =>
        if (lookup.serviceName == thisServiceName)
          system.scheduler.scheduleOnce(timeout.duration, self, ReturnResult(sender()))
        else
          println(s"Found lookup for [${lookup.serviceName}] but expected [$thisServiceName]")
      case ReturnResult(to) =>
        to ! Resolved(thisServiceName, servicesFound.toVector)
    }
  }

  val handler = system.actorOf(Props(new UdpHandler), "udp-discovery-handler")

  /**
   * Scala API: Perform lookup using underlying discovery implementation.
   *
   * @param lookup         A service discovery lookup.
   * @param resolveTimeout Timeout. Up to the discovery-mechanism to adhere to his
   */
  override def lookup(lookup: Lookup, resolveTimeout: FiniteDuration): Future[SimpleServiceDiscovery.Resolved] = {
    implicit val timeout = Timeout(resolveTimeout + 100.millis) // TODO: enough margin?
    handler.ask(DoLookup(lookup, resolveTimeout)).mapTo[SimpleServiceDiscovery.Resolved]
  }
}
