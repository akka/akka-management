/*
 * Copyright (C) 2017-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.discovery.kubernetes

import java.net.InetAddress
import java.nio.file.{Files, Paths}

import akka.actor.ActorSystem
import akka.annotation.InternalApi
import akka.discovery._
import akka.http.scaladsl._
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import com.typesafe.sslconfig.akka.AkkaSSLConfig
import com.typesafe.sslconfig.ssl.TrustStoreConfig

import scala.collection.immutable.Seq
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.util.Try
import JsonFormat._
import akka.discovery.ServiceDiscovery.{Resolved, ResolvedTarget}
import akka.discovery.kubernetes.PodList.Container

import scala.util.control.{NoStackTrace, NonFatal}
import akka.event.Logging

object KubernetesApiServiceDiscovery {

  /**
   * INTERNAL API
   *
   * Finds relevant targets given a pod list. Note that this doesn't filter by name as it is the job of the selector
   * to do that.
   */
  @InternalApi
  private[kubernetes] def targets(podList: PodList,
                                  portName: Option[String],
                                  podNamespace: String,
                                  podDomain: String): Seq[ResolvedTarget] =
    for {
      item <- podList.items
      if item.metadata.flatMap(_.deletionTimestamp).isEmpty
      itemSpec <- item.spec.toSeq
      itemStatus <- item.status.toSeq
      if itemStatus.phase.contains("Running")
      ip <- itemStatus.podIP.toSeq
      // Maybe port is an Option of a port, and will be None if no portName was requested
      maybePort <- portName match {
        case None =>
          Seq(None)
        case Some(name) =>
          for {
            container <- itemSpec.containers
            ports <- container.ports.toSeq
            port <- ports
            if port.name.contains(name)
          } yield Some(port.containerPort)
      }
    } yield {
      // This host may not be resolvable, for example when 'pods disabled' is configured
      // https://kubernetes.io/docs/tasks/administer-cluster/dns-custom-nameservers/#coredns-configmap-options
      val host = s"${ip.replace('.', '-')}.$podNamespace.pod.$podDomain"
      ResolvedTarget(
        host = host,
        port = maybePort,
        address = Some(InetAddress.getByName(ip))
      )
    }

  class KubernetesApiException(msg: String) extends RuntimeException(msg) with NoStackTrace

}

/**
 * An alternative implementation that uses the Kubernetes API. The main advantage of this method is that it allows
 * you to define readiness/health checks that don't affect the bootstrap mechanism.
 */
class KubernetesApiServiceDiscovery(system: ActorSystem) extends ServiceDiscovery {

  import akka.discovery.kubernetes.KubernetesApiServiceDiscovery._
  import system.dispatcher

  private val http = Http()(system)

  private val settings = Settings(system)

  private implicit val mat: ActorMaterializer = ActorMaterializer()(system)

  private val log = Logging(system, getClass)

  private val httpsTrustStoreConfig =
    TrustStoreConfig(data = None, filePath = Some(settings.apiCaPath)).withStoreType("PEM")

  private val httpsConfig =
    AkkaSSLConfig()(system).mapSettings(
        s => s.withTrustManagerConfig(s.trustManagerConfig.withTrustStoreConfigs(Seq(httpsTrustStoreConfig))))

  private val httpsContext = http.createClientHttpsContext(httpsConfig)

  log.debug("Settings {}", settings)

  override def lookup(query: Lookup, resolveTimeout: FiniteDuration): Future[Resolved] = {
    val labelSelector = settings.podLabelSelector(query.serviceName)

    log.info("Querying for pods with label selector: [{}]. Namespace: [{}]. Port: [{}]", labelSelector, podNamespace,
      query.portName)

    for {
      request <- optionToFuture(podRequest(apiToken, podNamespace, labelSelector),
        s"Unable to form request; check Kubernetes environment (expecting env vars ${settings.apiServiceHostEnvName}, ${settings.apiServicePortEnvName})")

      response <- http.singleRequest(request, httpsContext)

      entity <- response.entity.toStrict(resolveTimeout)

      podList <- {

        response.status match {
          case StatusCodes.OK =>
            log.debug("Kubernetes API entity: [{}]", entity.data.utf8String)
            val unmarshalled = Unmarshal(entity).to[PodList]
            unmarshalled.failed.foreach { t =>
              log.warning(
                  "Failed to unmarshal Kubernetes API response.  Status code: [{}]; Response body: [{}]. Ex: [{}]",
                  response.status.value, entity, t.getMessage)
            }
            unmarshalled
          case StatusCodes.Forbidden =>
            Unmarshal(entity).to[String].foreach { body =>
              log.warning("Forbidden to communicate with Kubernetes API server; check RBAC settings. Response: [{}]",
                body)
            }
            Future.failed(
                new KubernetesApiException(
                    "Forbidden when communicating with the Kubernetes API. Check RBAC settings."))
          case other =>
            Unmarshal(entity).to[String].foreach { body =>
              log.warning(
                "Non-200 when communicating with Kubernetes API server. Status code: [{}]. Response body: [{}]",
                other,
                body
              )
            }

            Future.failed(new KubernetesApiException(s"Non-200 from Kubernetes API server: $other"))
        }

      }

    } yield {
      val addresses = targets(podList, query.portName, podNamespace, settings.podDomain)
      if (addresses.isEmpty && podList.items.nonEmpty) {
        if (log.isInfoEnabled) {
          val containerPortNames = podList.items.flatMap(_.spec).flatMap(_.containers).flatMap(_.ports).flatten.toSet
          log.info(
            "No targets found from pod list. Is the correct port name configured? Current configuration: [{}]. Ports on pods: [{}]",
            query.portName,
            containerPortNames
          )
        }
      }
      Resolved(
        serviceName = query.serviceName,
        addresses = addresses
      )
    }
  }

  private val apiToken = readConfigVarFromFilesystem(settings.apiTokenPath, "api-token") getOrElse ""

  private val podNamespace = settings.podNamespace orElse
    readConfigVarFromFilesystem(settings.podNamespacePath, "pod-namespace") getOrElse "default"

  /**
   * This uses blocking IO, and so should only be used to read configuration at startup.
   */
  private def readConfigVarFromFilesystem(path: String, name: String): Option[String] = {
    val file = Paths.get(path)
    if (Files.exists(file)) {
      try {
        Some(new String(Files.readAllBytes(file), "utf-8"))
      } catch {
        case NonFatal(e) =>
          log.error(e, "Error reading {} from {}", name, path)
          None
      }
    } else {
      log.warning("Unable to read {} from {} because it doesn't exist.", name, path)
      None
    }
  }

  private def optionToFuture[T](option: Option[T], failMsg: String): Future[T] =
    option.fold(Future.failed[T](new NoSuchElementException(failMsg)))(Future.successful)

  private def podRequest(token: String, namespace: String, labelSelector: String) =
    for {
      host <- sys.env.get(settings.apiServiceHostEnvName)
      portStr <- sys.env.get(settings.apiServicePortEnvName)
      port <- Try(portStr.toInt).toOption
    } yield {
      val path = Uri.Path.Empty / "api" / "v1" / "namespaces" / namespace / "pods"
      val query = Uri.Query("labelSelector" -> labelSelector)
      val uri = Uri.from(scheme = "https", host = host, port = port).withPath(path).withQuery(query)

      HttpRequest(uri = uri, headers = Seq(Authorization(OAuth2BearerToken(token))))
    }
}
