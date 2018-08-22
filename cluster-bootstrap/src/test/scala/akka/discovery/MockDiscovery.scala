/*
 * Copyright (C) 2017 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.discovery

import java.util.concurrent.atomic.AtomicReference

import akka.actor.ActorSystem
import akka.annotation.InternalApi
import akka.discovery.SimpleServiceDiscovery.Resolved
import akka.event.Logging

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

@InternalApi
object MockDiscovery {
  private val data = new AtomicReference[Map[Lookup, () => Future[Resolved]]](Map.empty)

  def set(name: Lookup, to: () => Future[Resolved]): Unit = {
    val d = data.get()
    if (data.compareAndSet(d, d.updated(name, to))) ()
    else set(name, to) // retry
  }

  def remove(name: Lookup): Unit = {
    val d = data.get()
    if (data.compareAndSet(d, d - name)) ()
    else remove(name) // retry
  }
}

@InternalApi
final class MockDiscovery(system: ActorSystem) extends SimpleServiceDiscovery {

  private val log = Logging(system, getClass)

  override def lookup(query: Lookup, resolveTimeout: FiniteDuration): Future[Resolved] = {
    MockDiscovery.data.get().get(query) match {
      case Some(res) ⇒
        val items = res()
        log.info("Mock-resolved [{}] to [{}:{}]", query, items, items.value)
        items
      case None ⇒
        log.info("No mock-data for [{}], resolving as 'Nil'. Current mocks: {}", query, MockDiscovery.data.get())
        Future.successful(Resolved(query.serviceName, Nil))
    }
  }

}
