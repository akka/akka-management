/*
 * Copyright (C) 2017 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.discovery

import java.util.concurrent.atomic.AtomicReference

import akka.actor.{ ActorSystem, ExtendedActorSystem }
import akka.annotation.InternalApi
import akka.discovery.ServiceDiscovery.Resolved
import akka.event.Logging
import akka.io.{ Dns, IO }

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

@InternalApi
object MockDiscovery {
  private val data = new AtomicReference[Map[String, Resolved]](Map.empty)

  def set(name: String, to: Resolved): Unit = {
    val d = data.get()
    if (data.compareAndSet(d, d.updated(name, to))) ()
    else set(name, to) // retry
  }

  def remove(name: String): Unit = {
    val d = data.get()
    if (data.compareAndSet(d, d - name)) ()
    else remove(name) // retry
  }
}

@InternalApi
final class MockDiscovery(system: ActorSystem) extends ServiceDiscovery {

  private val log = Logging(system, getClass)

  override def lookup(name: String, resolveTimeout: FiniteDuration): Future[Resolved] =
    MockDiscovery.data.get().get(name) match {
      case Some(res) ⇒
        log.info("Mock-resolved [{}] to [{}]", name, res)
        Future.successful(res)
      case None ⇒
        log.info("No mock-data for [{}], resolving as 'Nil'", name)
        Future.successful(Resolved(name, Nil))
    }
}
