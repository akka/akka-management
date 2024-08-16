/*
 * Copyright (C) 2017-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.discovery.azureapi.rbac.aks

import akka.actor.ExtendedActorSystem
import akka.annotation.InternalApi
import akka.discovery.ServiceDiscovery.{ Resolved, ResolvedTarget }
import akka.discovery.azureapi.rbac.aks.AzureRbacAksServiceDiscovery._
import akka.discovery.azureapi.rbac.aks.JsonFormat._
import akka.discovery.{ Lookup, ServiceDiscovery }
import akka.dispatch.Dispatchers.DefaultBlockingDispatcherId
import akka.event.Logging
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{ Authorization, OAuth2BearerToken }
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
import scala.util.control.{ NoStackTrace, NonFatal }

/**
 * INTERNAL API
 *
 * Finds relevant targets given a pod list. Note that this doesn't filter by name as it is the job of the selector
 * to do that.
 */
@InternalApi
object AzureRbacAksServiceDiscovery {
  private def azureDefaultCredential: DefaultAzureCredential =
    new DefaultAzureCredentialBuilder().build()

  private val accessTokenRequestContext: TokenRequestContext =
    new TokenRequestContext()

  private[aks] def targets(
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

  final class AzureIdentityException(msg: String) extends RuntimeException(msg) with NoStackTrace

  final case class KubernetesSetup(namespace: String, ctx: HttpsConnectionContext)
}

/**
 * INTERNAL API
 *
 * Finds relevant targets given a pod list. Note that this doesn't filter by name as it is the job of the selector
 * to do that.
 */
@InternalApi
final class AzureRbacAksServiceDiscovery(implicit system: ExtendedActorSystem) extends ServiceDiscovery {
  private val http = Http()

  private val settings = Settings(system.settings.config.getConfig("akka.discovery.azure-rbac-aks-api"))

  private val log = Logging(system, classOf[AzureRbacAksServiceDiscovery])

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

  log.debug("Settings {}", settings)

  import system.dispatcher

  private def fetchAccessToken: Future[AccessToken] =
    azureDefaultCredential
      .getToken(accessTokenRequestContext.addScopes(settings.entraServerId))
      .onErrorMap { error =>
        log.error("[{}]", error)
        new AzureIdentityException(
          """
          |Attempt failed while fetching access token. Check if workload identity is enabled for the cluster or not and
          |if the pods has been injected with required AZURE environment variables
          |"""".stripMargin)
      }
      .toFuture
      .asScala

  private def parseKubernetesResponse(response: HttpResponse, entity: HttpEntity.Strict): Future[PodList] =
    response.status match {
      case StatusCodes.OK =>
        log.debug("Kubernetes API entity: [{}]", entity.data.utf8String)
        Unmarshal(entity).to[PodList].recoverWith {
          case exception =>
            log.warning(
              "Failed to unmarshal Kubernetes API response.  Status code: [{}]; Response body: [{}]. Ex: [{}]",
              response.status.value,
              entity,
              exception.getMessage)

            Future.failed(new KubernetesApiException("Failed to unmarshal Kubernetes API response."))
        }
      case StatusCodes.Forbidden =>
        Unmarshal(entity).to[String].flatMap { body =>
          log.warning("Forbidden to communicate with Kubernetes API server; check RBAC settings. Response: [{}]", body)

          Future.failed(
            new KubernetesApiException(
              """
              |Forbidden when communicating with the Kubernetes API Server. Check if the managed identity has the appropriate role
              |assigment(example: Azure Pod Reader) or if workload identity is enabled for the cluster.
              |""".stripMargin
            ))
        }
      case other =>
        Unmarshal(entity).to[String].flatMap { body =>
          log.warning(
            "Non-200 when communicating with Kubernetes API server. Status code: [{}]. Response body: [{}]",
            other,
            body
          )

          Future.failed(new KubernetesApiException(s"Non-200 from Kubernetes API server: $other"))
        }
    }

  private def pods(ctx: HttpsConnectionContext, request: HttpRequest, timeout: FiniteDuration): Future[PodList] = {
    for {
      response: HttpResponse <- http.singleRequest(request, ctx)
      entity <- response.entity.toStrict(timeout)
      pods <- parseKubernetesResponse(response, entity)
    } yield pods
  }

  override def lookup(lookup: Lookup, resolveTimeout: FiniteDuration): Future[ServiceDiscovery.Resolved] = {
    val selector = settings.podLabelSelector.format(lookup.serviceName)

    for {
      ks <- kubernetesSetup
      token <- fetchAccessToken.map(_.getToken)
      request <- podRequest(token, ks.namespace, selector)
      pods <- pods(ks.ctx, request, resolveTimeout)
    } yield {
      val addresses =
        targets(pods, lookup.portName, ks.namespace, settings.podDomain, settings.rawIp, settings.containerName)
      if (addresses.isEmpty && pods.items.nonEmpty) {
        val containerPortNames = pods.items.flatMap(_.spec).flatMap(_.containers).flatMap(_.ports).flatten.toSet
        log.warning(
          "No targets found from pod list. Is the correct port name configured? Current configuration: [{}]. Ports on pods: [{}]",
          lookup.portName,
          containerPortNames
        )
      }
      Resolved(
        serviceName = lookup.serviceName,
        addresses = addresses
      )
    }
  }

  private def podRequest(token: String, namespace: String, labelSelector: String) = {
    val host = settings.apiServiceHost
    val port = settings.apiServicePort
    val path = Uri.Path.Empty / "api" / "v1" / "namespaces" / namespace / "pods"
    val query = Uri.Query("labelSelector" -> labelSelector)
    val uri = Uri.from(scheme = "https", host = host, port = port).withPath(path).withQuery(query)

    Future(HttpRequest(uri = uri, headers = List(Authorization(OAuth2BearerToken(token)))))
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
        Some(Files.readString(file))
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
