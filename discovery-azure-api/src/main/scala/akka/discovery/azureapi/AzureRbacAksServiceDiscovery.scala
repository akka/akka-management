/*
 * Copyright (C) 2017-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.discovery.azureapi

import akka.actor.ExtendedActorSystem
import akka.annotation.InternalApi
import akka.discovery.ServiceDiscovery.{ Resolved, ResolvedTarget }
import akka.discovery.azureapi.AzureRbacAksServiceDiscovery._
import akka.discovery.azureapi.JsonFormat._
import akka.discovery.{ Lookup, ServiceDiscovery }
import akka.dispatch.Dispatchers.DefaultBlockingDispatcherId
import akka.event.Logging
import akka.http.scaladsl.model.headers.{ Authorization, OAuth2BearerToken }
import akka.http.scaladsl.model.{ HttpRequest, StatusCodes, Uri }
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.http.scaladsl.{ ConnectionContext, Http, HttpsConnectionContext }
import akka.pki.kubernetes.PemManagersProvider
import com.azure.core.credential.{ AccessToken, TokenRequestContext }
import com.azure.identity.{ DefaultAzureCredential, DefaultAzureCredentialBuilder }

import java.net.InetAddress
import java.nio.file.{ Files, Paths }
import java.security.{ KeyStore, SecureRandom }
import javax.net.ssl.{ KeyManager, KeyManagerFactory, SSLContext, TrustManager }
import scala.collection.immutable
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ ExecutionContext, Future }
import scala.jdk.FutureConverters._
import scala.util.Try
import scala.util.control.{ NoStackTrace, NonFatal }

object AzureRbacAksServiceDiscovery {
  private def azureDefaultCredential: DefaultAzureCredential =
    new DefaultAzureCredentialBuilder().build()

  private val accessTokenRequestContext: TokenRequestContext =
    new TokenRequestContext()

  /**
   * INTERNAL API
   *
   * Finds relevant targets given a pod list. Note that this doesn't filter by name as it is the job of the selector
   * to do that.
   */
  @InternalApi
  private[azureapi] def targets(
      podList: PodList,
      portName: Option[String],
      podNamespace: String,
      podDomain: String,
      rawIp: Boolean,
      containerName: Option[String]): immutable.Seq[ResolvedTarget] =
    for {
      item <- podList.items
      if item.metadata.flatMap(_.deletionTimestamp).isEmpty
      itemSpec <- item.spec.toSeq
      itemStatus <- item.status.toSeq
      if itemStatus.phase.contains("Running")
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

  final class KubernetesApiException(msg: String) extends RuntimeException(msg) with NoStackTrace

  final case class KubernetesSetup(namespace: String, ctx: HttpsConnectionContext)
}

class AzureRbacAksServiceDiscovery(implicit system: ExtendedActorSystem) extends ServiceDiscovery {
  private val http = Http()

  private val settings = Settings(system)

  private val log = Logging(system, classOf[AzureRbacAksServiceDiscovery])

  log.debug("Settings {}", settings)

  private def fetchToken: Future[AccessToken] =
    azureDefaultCredential.getToken(accessTokenRequestContext.addScopes(settings.entraServerId)).toFuture.asScala

  private val kubernetesSetup = {
    implicit val blockingDispatcher: ExecutionContext = system.dispatchers.lookup(DefaultBlockingDispatcherId)
    for {
      namespace: String <- Future {
        settings.podNamespace
          .orElse(readConfigVarFromFilesystem(settings.podNamespacePath, "pod-namespace"))
          .getOrElse("default")
      }
      httpsContext <- Future(clientHttpsConnectionContext())
    } yield {
      KubernetesSetup(namespace, httpsContext)
    }
  }

  import system.dispatcher

  private def accessToken(retries: Int = 3): Future[AccessToken] = {
    fetchToken.flatMap {
      case token if token.isExpired && retries > 0 => accessToken(retries - 1)
      case token if token.isExpired =>
        Future.failed(new RuntimeException("Failed to fetch a valid token after multiple attempts"))
      case token => Future.successful(token)
    }
  }

  override def lookup(lookup: Lookup, resolveTimeout: FiniteDuration): Future[ServiceDiscovery.Resolved] = {
    val selector = settings.podLabelSelector(lookup.serviceName)

    for {
      ks <- kubernetesSetup

      at <- accessToken()

      request <- {
        log.info(
          "Querying for pods with label selector: [{}]. Namespace: [{}]. Port: [{}]",
          selector,
          ks.namespace,
          lookup.portName
        )

        optionToFuture(
          // FIXME | remove .get
          podRequest(at.getToken, ks.namespace, selector),
          s"Unable to form request; check Kubernetes environment (expecting env vars ${settings.apiServiceHostEnvName}, ${settings.apiServicePortEnvName})"
        )
      }

      response <- http.singleRequest(request, ks.ctx)

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
        targets(podList, lookup.portName, ks.namespace, settings.podDomain, settings.rawIp, settings.containerName)
      if (addresses.isEmpty && podList.items.nonEmpty) {
        if (log.isInfoEnabled) {
          val containerPortNames = podList.items.flatMap(_.spec).flatMap(_.containers).flatMap(_.ports).flatten.toSet
          log.info(
            "No targets found from pod list. Is the correct port name configured? Current configuration: [{}]. Ports on pods: [{}]",
            lookup.portName,
            containerPortNames
          )
        }
      }
      Resolved(
        serviceName = lookup.serviceName,
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
      val query = Uri.Query("labelSelector" -> labelSelector)
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
