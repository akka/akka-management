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

  private val config = system.settings.config

  private val serviceFile =
    parseServiceFileName(config.getString("akka.discovery.akka-local.service-file"))
  private val hostname =
    if (config.hasPath("akka.management.http.hostname"))
      config.getString("akka.management.http.hostname")
    else
      config.getString("akka.remote.netty.tcp.hostname")
  // we need to read the http.port here since the BootstrapCoordinator assumes that Resolved contains Some(httpPort) or None.
  // Since we want to be able to start multiple instances on one host we need to save the port
  private val port = system.settings.config.getInt("akka.management.http.port")
  private val localServiceRegistration = new LocalServiceRegistration(Paths.get(serviceFile))

  override def lookup(name: String, resolveTimeout: FiniteDuration): Future[Resolved] = {
    // we need to register ourselves if not already registered
    registerSelf()

    log.debug(s"Using $serviceFile as service file")
    val addresses =
      localServiceRegistration.localServiceEntries.map { entry =>
        ResolvedTarget(entry.addr, Some(entry.port))
      }

    // remove self from the service file on shutdown
    sys.addShutdownHook {
      unregisterSelf()
    }

    log.info(s"Discovered $addresses")
    Future.successful(Resolved(name, addresses))
  }

  private def registerSelf(): Unit = {
    log.debug("Checking if self is registered in service file")

    if (!localServiceRegistration.isRegistered(hostname, port)) {
      log.info(s"Registering $hostname:$port")
      localServiceRegistration.add(hostname, port)
    }
  }

  private def unregisterSelf(): Unit = {
    log.info(s"Unregistering $hostname:$port from $serviceFile")
    localServiceRegistration.remove(hostname, port)
  }

  private def parseServiceFileName(fromConfig: String) =
    if (fromConfig.contains("<name>")) fromConfig.replace("<name>", system.name)
    else fromConfig
}
