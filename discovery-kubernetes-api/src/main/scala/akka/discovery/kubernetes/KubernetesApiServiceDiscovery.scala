/*
 * Copyright (C) 2017-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.discovery.kubernetes

import java.net.InetAddress
import java.nio.file.Files
import java.nio.file.Paths
import java.security.KeyStore
import java.security.SecureRandom

import scala.collection.immutable
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.util.Try
import scala.util.control.NoStackTrace
import scala.util.control.NonFatal

import akka.actor.ActorSystem
import akka.annotation.InternalApi
import akka.discovery.ServiceDiscovery.Resolved
import akka.discovery.ServiceDiscovery.ResolvedTarget
import akka.discovery._
import akka.discovery.kubernetes.JsonFormat._
import akka.dispatch.Dispatchers.DefaultBlockingDispatcherId
import akka.event.LoggingAdapter
import akka.event.Logging
import akka.http.scaladsl.HttpsConnectionContext
import akka.http.scaladsl._
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.Authorization
import akka.http.scaladsl.model.headers.OAuth2BearerToken
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.pki.kubernetes.PemManagersProvider
import javax.net.ssl.KeyManager
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager

object KubernetesApiServiceDiscovery {

  /**
   * INTERNAL API
   *
   * Finds relevant targets given a pod list. Note that this doesn't filter by name as it is the job of the selector
   * to do that.
   */
  @InternalApi
  private[kubernetes] def targets(
      podList: PodList,
      portName: Option[String],
      podNamespace: String,
      podDomain: String,
      rawIp: Boolean,
      containerName: Option[String],
      onlyReady: Boolean): immutable.Seq[ResolvedTarget] = {

    val readyFilter: PodList.PodStatus => Boolean =
      if (onlyReady) _.conditions.iterator.flatten.exists(_.isAbleToServeRequests)
      else _ => true

    for {
      item <- podList.items
      if item.metadata.flatMap(_.deletionTimestamp).isEmpty
      itemSpec <- item.spec.toSeq
      itemStatus <- item.status.toSeq
      if itemStatus.phase.contains("Running")
      if readyFilter(itemStatus)
      if containerName.forall(name =>
        itemStatus.containerStatuses match {
          case Some(statuses) => statuses.filter(_.name == name).exists(!_.state.contains("waiting"))
          case None           => false
        })
      ip <- itemStatus.podIP.toSeq
      // Maybe port is an Option of a port, and will be None if no portName was requested
      maybePort <- portName match {
        case None =>
          List(None)
        case Some(name) =>
          for {
            container <- itemSpec.containers
            ports <- container.ports.toSeq
            port <- ports
            if port.name.contains(name)
          } yield Some(port.containerPort)
      }
    } yield {
      val hostOrIp = if (rawIp) ip else s"${ip.replace('.', '-')}.$podNamespace.pod.$podDomain"
      ResolvedTarget(
        host = hostOrIp,
        port = maybePort,
        address = Some(InetAddress.getByName(ip))
      )
    }
  }

  class KubernetesApiException(msg: String) extends RuntimeException(msg) with NoStackTrace
}

object BaseKubernetesApiServiceDiscovery {

  private final case class KubernetesSetup(
      podNamespace: String,
      apiToken: String,
      clientHttpsConnectionContext: HttpsConnectionContext)

}

/**
 * Discovery implementation that uses the Kubernetes API.
 *
 */
sealed abstract class BaseKubernetesApiServiceDiscovery(protected val log: LoggingAdapter)(implicit system: ActorSystem)
    extends ServiceDiscovery {

  import BaseKubernetesApiServiceDiscovery.KubernetesSetup
  import akka.discovery.kubernetes.KubernetesApiServiceDiscovery._

  private val http = Http()

  private val settings = Settings(system)

  protected def onlyDiscoverReady: Boolean

  log.debug("Settings {}", settings)

  private val kubernetesSetup: Future[KubernetesSetup] = {
    implicit val blockingDispatcher: ExecutionContext = system.dispatchers.lookup(DefaultBlockingDispatcherId)
    for {
      apiToken: String <- Future {
        readConfigVarFromFilesystem(settings.apiTokenPath, "api-token").getOrElse("")
      }
      namespace: String <- Future {
        settings.podNamespace
          .orElse(readConfigVarFromFilesystem(settings.podNamespacePath, "pod-namespace"))
          .getOrElse("default")
      }
      httpsContext <- Future(clientHttpsConnectionContext())
    } yield {
      KubernetesSetup(namespace, apiToken, httpsContext)
    }
  }

  import system.dispatcher

  override def lookup(query: Lookup, resolveTimeout: FiniteDuration): Future[Resolved] = {
    val labelSelector = settings.podLabelSelector(query.serviceName)

    for {
      setup <- kubernetesSetup

      request <- {
        log.info(
          "Querying for pods with label selector: [{}]. Namespace: [{}]. Port: [{}]",
          labelSelector,
          setup.podNamespace,
          query.portName)

        optionToFuture(
          podRequest(setup.apiToken, setup.podNamespace, labelSelector),
          s"Unable to form request; check Kubernetes environment (expecting env vars ${settings.apiServiceHostEnvName}, ${settings.apiServicePortEnvName})"
        )
      }

      response <- http.singleRequest(request, setup.clientHttpsConnectionContext)

      entity <- response.entity.toStrict(resolveTimeout)

      podList <- {

        response.status match {
          case StatusCodes.OK =>
            log.debug("Kubernetes API entity: [{}]", entity.data.utf8String)
            val unmarshalled = Unmarshal(entity).to[PodList]
            unmarshalled.failed.foreach { t =>
              log.warning(
                "Failed to unmarshal Kubernetes API response.  Status code: [{}]; Response body: [{}]. Ex: [{}]",
                response.status.value,
                entity,
                t.getMessage)
            }
            unmarshalled
          case StatusCodes.Forbidden =>
            Unmarshal(entity).to[String].foreach { body =>
              log.warning(
                "Forbidden to communicate with Kubernetes API server; check RBAC settings. Response: [{}]",
                body)
            }
            Future.failed(
              new KubernetesApiException("Forbidden when communicating with the Kubernetes API. Check RBAC settings."))
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
      val addresses =
        targets(
          podList,
          query.portName,
          setup.podNamespace,
          settings.podDomain,
          settings.rawIp,
          settings.containerName,
          onlyDiscoverReady)
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

  private def optionToFuture[T](option: Option[T], failMsg: String): Future[T] =
    option.fold(Future.failed[T](new NoSuchElementException(failMsg)))(Future.successful)

  private def podRequest(token: String, namespace: String, labelSelector: String) =
    for {
      host <- sys.env.get(settings.apiServiceHostEnvName)
      portStr <- sys.env.get(settings.apiServicePortEnvName)
      port <- Try(portStr.toInt).toOption
    } yield {
      val path = Uri.Path.Empty / "api" / "v1" / "namespaces" / namespace / "pods"
      val query = Uri.Query("labelSelector" -> labelSelector, "fieldSelection" -> "status.phase==Running")
      val uri = Uri.from(scheme = "https", host = host, port = port).withPath(path).withQuery(query)

      HttpRequest(uri = uri, headers = List(Authorization(OAuth2BearerToken(token))))
    }

  /**
   * This uses blocking IO, and so should only be used at startup from blocking dispatcher.
   */
  private def clientHttpsConnectionContext(): HttpsConnectionContext = {
    val certificates = PemManagersProvider.loadCertificates(settings.apiCaPath)
    val factory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm)
    val keyStore = KeyStore.getInstance("PKCS12")
    keyStore.load(null)
    factory.init(keyStore, Array.empty)
    val km: Array[KeyManager] = factory.getKeyManagers
    val tm: Array[TrustManager] =
      PemManagersProvider.buildTrustManagers(certificates)
    val random: SecureRandom = new SecureRandom
    val sslContext = SSLContext.getInstance("TLSv1.2")
    sslContext.init(km, tm, random)
    ConnectionContext.httpsClient(sslContext)
  }

  /**
   * This uses blocking IO, and so should only be used to read configuration at startup from blocking dispatcher.
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

}

/**
 * An implementation which will discover Kubernetes pods even if they are not ready to serve external traffic.
 * This discovery method is suitable for bootstrapping a cluster, even if readiness/health checks will not pass
 * until the cluster is bootstrapped.
 *
 * If used to discover external Kubernetes services, not all pods discovered will be able to serve traffic.
 */
class KubernetesApiServiceDiscovery(system: ActorSystem)
    extends BaseKubernetesApiServiceDiscovery(Logging(system, classOf[KubernetesApiServiceDiscovery]))(system) {
  override final protected def onlyDiscoverReady: Boolean = false
}

/**
 * An implementation which will discover Kubernetes pods only if they are ready to serve external traffic.
 *
 * NOT SUITABLE FOR CLUSTER BOOTSTRAP!
 */
class ExternalKubernetesApiServiceDiscovery(system: ActorSystem)
    extends BaseKubernetesApiServiceDiscovery(Logging(system, classOf[ExternalKubernetesApiServiceDiscovery]))(system) {
  override final protected def onlyDiscoverReady: Boolean = true
}
