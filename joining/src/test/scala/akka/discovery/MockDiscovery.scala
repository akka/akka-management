package akka.discovery

import akka.actor.ActorSystem
import akka.discovery.ServiceDiscovery
import akka.discovery.ServiceDiscovery.Resolved
import akka.event.Logging

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

object MockDiscovery {
  @volatile private var map = Map[String, Resolved]()
}

class MockDiscovery(system: ActorSystem) extends ServiceDiscovery {

  private val log = Logging(system, classOf[MockDiscovery])
  log.info(Console.YELLOW + s"Using {} for service discovery implementation." + Console.RESET, Logging.simpleName(getClass))


  def define(name: String, resolved: Resolved): Unit = {
    MockDiscovery.map = MockDiscovery.map.updated(name, resolved)
  }

  def lookup(name: String, resolveTimeout: FiniteDuration): Future[Resolved] = {
    val resolved = MockDiscovery.map.get(name)
    log.debug("Mock DNS lookup for: {} resolved to: {}", name, resolved)
    Future.successful(resolved)
  }

}
