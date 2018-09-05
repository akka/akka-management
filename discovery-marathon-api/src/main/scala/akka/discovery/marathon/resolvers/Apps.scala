/*
 * Copyright (C) 2017 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.discovery.marathon.resolvers

import akka.discovery.SimpleServiceDiscovery.ResolvedTarget
import akka.discovery.marathon.Settings
import akka.discovery.marathon.services.App

object Apps {

  /**
   * Resolves target addresses for the supplied apps.
   *
   * Check the Marathon Networking docs for more info on the various restrictions it imposes
   * on the network config - https://mesosphere.github.io/marathon/docs/networking.html
   *
   * @param apps list of apps which are expected to host akka cluster nodes
   * @param settings marathon discovery settings
   * @param serviceName akka cluster actor system name
   * @return a list of resolved targets
   */
  def resolve(apps: Seq[App], settings: Settings, serviceName: String): Seq[ResolvedTarget] = {
    apps.flatMap { app =>
      // multiple networks with different modes are not allowed
      val networkMode = app.networks.map(_.mode).headOption

      app.tasks.flatMap { task =>
        val taskPorts = networkMode match {
          case Some("host") =>
            app.portDefinitionsSeq.flatMap(_.name).zip(task.ports)

          case Some("container/bridge") =>
            app.container.map { container =>
              container.portMappingsSeq.flatMap(_.name).zip(task.ports)
            }.getOrElse(Seq.empty)

          case Some("container") =>
            app.container.map { container =>
              container.portMappingsSeq.flatMap { mapping =>
                mapping.name.map(name => (name, mapping.containerPort))
              }
            }.getOrElse(Seq.empty)

          case _ =>
            Seq.empty
        }

        // port names are required to be unique, so at most one akka management port should be available
        val servicePort = taskPorts.collectFirst {
          case port if port._1 == settings.servicePortName =>
            port._2
        }

        servicePort.flatMap { _ =>
          // exposing the same port on multiple addresses is not allowed
          task.ipAddresses.map(_.ipAddress).collectFirst {
            case address if settings.expectedClusterSubnet.forall(_.isInRange(address)) =>
              ResolvedTarget(host = address, servicePort)
          }
        }
      }
    }
  }
}
