/*
 * Copyright (C) 2017 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.discovery.marathon.resolvers

import akka.discovery.SimpleServiceDiscovery.ResolvedTarget
import akka.discovery.marathon.Settings
import akka.discovery.marathon.services.Pod

object Pods {

  /**
   * Resolves target addresses for the supplied pods.
   *
   * Check the Marathon Networking and Pods docs for more info on the various restrictions it imposes
   * on the config - https://mesosphere.github.io/marathon/docs/networking.html and
   * https://mesosphere.github.io/marathon/docs/pods.html
   *
   * @param pods list of pods which are expected to host akka cluster nodes
   * @param settings marathon discovery settings
   * @param serviceName akka cluster actor system name
   * @return a list of resolved targets
   * @return
   */
  def resolve(pods: Seq[Pod], settings: Settings, serviceName: String): Seq[ResolvedTarget] = {
    pods.flatMap { pod =>
      // retrieves the network name (if any) of the akka management port
      val expectedNetworkName = pod.spec.containers
        .flatMap(_.endpointsSeq.filter(_.name == settings.servicePortName))
        .flatMap(_.networkNamesSeq)
        .headOption

      // multiple networks with different modes are not allowed
      val mode = pod.spec.networksSeq.headOption.map(_.mode)

      pod.instances.flatMap { instance =>
        val instancePorts = mode match {
          case Some("host") =>
            instance.containers.flatMap(_.endpointsSeq).filter(isExpectedEndpoint(_, expectedNetworkName)).flatMap {
              endpoint =>
                endpoint.allocatedHostPort.map { port =>
                  (endpoint.name, port)
                }
            }

          case Some("container") =>
            pod.spec.containers.flatMap(_.endpointsSeq).filter(isExpectedEndpoint(_, expectedNetworkName)).flatMap {
              endpoint =>
                endpoint.containerPort.map { port =>
                  (endpoint.name, port)
                }
            }

          case Some("container/bridge") =>
            // bridge mode is not supported for pods
            Seq.empty

          case _ =>
            Seq.empty
        }

        // endpoint names are required to be unique, so at most one akka management endpoint should be available
        val servicePort = instancePorts.collectFirst {
          case port if port._1 == settings.servicePortName =>
            port._2
        }

        servicePort.flatMap { _ =>
          instance.networks.flatMap { network =>
            network.addresses.headOption
          }
          // exposing the same port on multiple addresses is not allowed
          .collectFirst {
            case address if settings.expectedClusterSubnet.forall(_.isInRange(address)) =>
              ResolvedTarget(host = address, servicePort)
          }
        }
      }
    }
  }

  /**
   * Checks if the specified endpoint is configured on the specified network.
   *
   * @param endpoint endpoint to check
   * @param expectedNetworkName expected network
   * @return `true`, if the endpoint is on the network or
   *        if the endpoint doesn't have a network and no network is expected
   */
  def isExpectedEndpoint(
      endpoint: Pod.Endpoint,
      expectedNetworkName: Option[String]
  ): Boolean = {
    expectedNetworkName.forall(endpoint.networkNamesSeq.contains)
  }
}
