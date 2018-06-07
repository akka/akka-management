/*
 * Copyright (C) 2017 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.discovery.local

import java.nio.file.Paths

import akka.actor.ActorSystem
import akka.discovery.SimpleServiceDiscovery
import akka.discovery.local.registration.LocalServiceRegistration
import akka.event.Logging

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

/**
 * Looks for an entry in the service file
 */
class LocalServiceDiscovery(system: ActorSystem) extends SimpleServiceDiscovery {
  import SimpleServiceDiscovery._

  private val log = Logging(system, getClass)
  private val serviceFile =
    system.settings.config.getString("akka.discovery.akka-local.service-file")
  private val hostname =
    system.settings.config.getString("akka.remote.netty.tcp.hostname")
  private val port = system.settings.config.getInt("akka.management.http.port")
  private val localServiceRegistration = new LocalServiceRegistration(Paths.get(serviceFile))

  override def lookup(name: String, resolveTimeout: FiniteDuration): Future[Resolved] = {
    registerSelf()

    log.info(s"Using $serviceFile as service file")
    val addresses =
      localServiceRegistration.localServiceEntries.map { entry =>
        ResolvedTarget(entry.addr, Some(entry.port))
      }

    sys.addShutdownHook {
      unregisterSelf()
    }

    log.info(s"Discovered $addresses")
    Future.successful(Resolved(name, addresses))
  }

  private def registerSelf(): Unit = {
    log.info("Checking if self is registered in service file")

    if (!localServiceRegistration.isRegistered(hostname, port)) {
      log.info(s"Registering $hostname:$port")
      localServiceRegistration.add(hostname, port)
    }
  }

  private def unregisterSelf(): Unit = {
    log.info(s"Unregistering $hostname:$port from $serviceFile")
    localServiceRegistration.remove(hostname, port)
  }
}
