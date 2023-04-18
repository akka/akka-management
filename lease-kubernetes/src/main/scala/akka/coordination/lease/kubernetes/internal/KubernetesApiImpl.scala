/*
 * Copyright (C) 2017-2023 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.coordination.lease.kubernetes.internal

import java.nio.file.Files
import java.nio.file.Paths
import java.security.KeyStore
import java.security.SecureRandom

import scala.collection.immutable
import scala.concurrent.Future
import scala.util.control.NonFatal

import akka.Done
import akka.actor.ActorSystem
import akka.annotation.InternalApi
import akka.coordination.lease.LeaseException
import akka.coordination.lease.LeaseTimeoutException
import akka.coordination.lease.kubernetes.KubernetesApi
import akka.coordination.lease.kubernetes.KubernetesSettings
import akka.coordination.lease.kubernetes.LeaseResource
import akka.event.Logging
import akka.http.scaladsl.ConnectionContext
import akka.http.scaladsl.Http
import akka.http.scaladsl.HttpsConnectionContext
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.Authorization
import akka.http.scaladsl.model.headers.OAuth2BearerToken
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.pattern.after
import akka.pki.kubernetes.PemManagersProvider
import javax.net.ssl.KeyManager
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager

/**
 * Could be shared between leases: https://github.com/akka/akka-management/issues/680
 * INTERNAL API
 */
@InternalApi private[akka] class KubernetesApiImpl(system: ActorSystem, settings: KubernetesSettings)
    extends KubernetesApi
    with KubernetesJsonSupport {

  import system.dispatcher

  private implicit val sys: ActorSystem = system
  // FIXME the asInstanceOf is because Scala3 complains
  private val log = Logging(system, getClass.asInstanceOf[Class[Any]])
  private val http = Http()(system)

  private lazy val sslContext = {
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
    sslContext
  }

  private lazy val clientSslContext: HttpsConnectionContext = ConnectionContext.httpsClient(sslContext)

  private val namespace =
    settings.namespace.orElse(readConfigVarFromFilesystem(settings.namespacePath, "namespace")).getOrElse("default")

  private val scheme = if (settings.secure) "https" else "http"
  private lazy val apiToken = readConfigVarFromFilesystem(settings.apiTokenPath, "api-token").getOrElse("")
  private lazy val headers = if (settings.secure) immutable.Seq(Authorization(OAuth2BearerToken(apiToken))) else Nil

  log.debug("kubernetes access namespace: {}. Secure: {}", namespace, settings.secure)

  /*
  PATH: to get all: /apis/akka.io/v1/namespaces/<namespace>/leases
  PATH: to get a specific one: /apis/akka.io/v1/namespaces/<namespace>/leases/<lease-name>
  curl -v -X POST localhost:8080/apis/akka.io/v1/namespaces/lease/leases/ -H "Content-Type: application/yaml" --data-binary "@lease-example.yml"

  responds with either:
  409 Conflict Already Exists

  OR

  201 Created if it works
   */
  override def readOrCreateLeaseResource(name: String): Future[LeaseResource] = {
    // TODO backoff retry
    val maxTries = 5

    def loop(tries: Int = 0): Future[LeaseResource] = {
      log.debug("Trying to create lease {}", tries)
      for {
        olr <- getLeaseResource(name)
        lr <- olr match {
          case Some(found) =>
            log.debug("{} already exists. Returning {}", name, found)
            Future.successful(found)
          case None =>
            log.info("lease {} does not exist, creating", name)
            createLeaseResource(name).flatMap {
              case Some(created) => Future.successful(created)
              case None =>
                if (tries < maxTries) loop(tries + 1)
                else Future.failed(new LeaseException(s"Unable to create or read lease after $maxTries tries"))
            }
        }
      } yield lr
    }

    loop()
  }

  /*
curl -v -X PUT localhost:8080/apis/akka.io/v1/namespaces/lease/leases/sbr-lease --data-binary "@sbr-lease.yml" -H "Content-Type: application/yaml"
PUTs must contain resourceVersions. Response:
409: Resource version is out of date
200 if it is updated
   */
  /**
   * Update the named resource.
   *
   * Must [[readOrCreateLeaseResource]] to first to get a resource version.
   *
   * Can return one of three things:
   *  - Future.Failure, e.g. timed out waiting for k8s api server to respond
   *  - Future.sucess[Left(resource)]: the update failed due to version not matching current in the k8s api server.
   *    In this case the current resource is returned so the version can be used for subsequent calls
   *  - Future.sucess[Right(resource)]: Returns the LeaseResource that contains the clientName and new version.
   *    The new version should be used for any subsequent calls
   */
  override def updateLeaseResource(
      leaseName: String,
      ownerName: String,
      version: String,
      time: Long = System.currentTimeMillis()): Future[Either[LeaseResource, LeaseResource]] = {
    val lcr = LeaseCustomResource(Metadata(leaseName, Some(version)), Spec(ownerName, System.currentTimeMillis()))
    for {
      entity <- Marshal(lcr).to[RequestEntity]
      response <- {
        log.debug("updating {} to {}", leaseName, lcr)
        makeRequest(
          requestForPath(pathForLease(leaseName), method = HttpMethods.PUT, entity),
          s"Timed out updating lease [$leaseName] to owner [$ownerName]. It is not known if the update happened"
        )
      }
      result <- response.status match {
        case StatusCodes.OK =>
          Unmarshal(response.entity)
            .to[LeaseCustomResource]
            .map(updatedLcr => {
              log.debug("LCR after update: {}", updatedLcr)
              Right(toLeaseResource(updatedLcr))
            })
        case StatusCodes.Conflict =>
          getLeaseResource(leaseName).flatMap {
            case None =>
              Future.failed(
                new LeaseException(s"GET after PUT conflict did not return a lease. Lease[${leaseName}-${ownerName}]"))
            case Some(lr) =>
              log.debug("LeaseResource read after conflict: {}", lr)
              Future.successful(Left(lr))
          }
        case StatusCodes.Unauthorized =>
          handleUnauthorized(response)
        case unexpected =>
          Unmarshal(response.entity)
            .to[String]
            .flatMap(body => {
              Future.failed(
                new LeaseException(
                  s"PUT for lease $leaseName returned unexpected status code ${unexpected}. Body: ${body}"))
            })
      }
    } yield result
  }

  private[akka] def removeLease(name: String): Future[Done] = {
    for {
      response <- makeRequest(
        requestForPath(pathForLease(name), HttpMethods.DELETE),
        s"Timed out removing lease [$name]. It is not known if the remove happened")

      result <- response.status match {
        case StatusCodes.OK =>
          log.debug("Lease deleted {}", name)
          response.discardEntityBytes()
          Future.successful(Done)
        case StatusCodes.NotFound =>
          log.debug("Lease already deleted {}", name)
          response.discardEntityBytes()
          Future.successful(Done) // already deleted
        case StatusCodes.Unauthorized =>
          handleUnauthorized(response)
        case unexpected =>
          Unmarshal(response.entity)
            .to[String]
            .flatMap(body => {
              Future.failed(
                new LeaseException(s"Unexpected status code when deleting lease. Status: $unexpected. Body: $body"))
            })
      }
    } yield result
  }

  private def getLeaseResource(name: String): Future[Option[LeaseResource]] = {
    val fResponse = makeRequest(requestForPath(pathForLease(name)), s"Timed out reading lease ${name}")
    for {
      response <- fResponse
      entity <- response.entity.toStrict(settings.bodyReadTimeout)
      lr <- response.status match {
        case StatusCodes.OK =>
          // it exists, parse it
          log.debug("Resource {} exists: {}", name, entity)
          Unmarshal(entity)
            .to[LeaseCustomResource]
            .map(lcr => {
              Some(toLeaseResource(lcr))
            })
        case StatusCodes.NotFound =>
          response.discardEntityBytes()
          log.debug("Resource does not exist: {}", name)
          Future.successful(None)
        case StatusCodes.Unauthorized =>
          handleUnauthorized(response)
        case unexpected =>
          Unmarshal(response.entity)
            .to[String]
            .flatMap(body => {
              Future.failed(new LeaseException(
                s"Unexpected response from API server when retrieving lease StatusCode: ${unexpected}. Body: ${body}"))
            })
      }
    } yield lr
  }

  private def handleUnauthorized(response: HttpResponse) = {
    Unmarshal(response.entity)
      .to[String]
      .flatMap(body => {
        Future.failed(new LeaseException(
          s"Unauthorized to communicate with Kubernetes API server. See https://doc.akka.io/docs/akka-management/current/kubernetes-lease.html#role-based-access-control for setting up access control. Body: ${body}"))
      })
  }

  private def pathForLease(name: String): Uri.Path =
    Uri.Path.Empty / "apis" / "akka.io" / "v1" / "namespaces" / namespace / "leases" / name
      .replaceAll("[^\\d\\w\\-\\.]", "")
      .toLowerCase

  private def requestForPath(
      path: Uri.Path,
      method: HttpMethod = HttpMethods.GET,
      entity: RequestEntity = HttpEntity.Empty) = {
    val uri = Uri.from(scheme = scheme, host = settings.apiServerHost, port = settings.apiServerPort).withPath(path)
    HttpRequest(uri = uri, headers = headers, method = method, entity = entity)
  }

  private def makeRequest(request: HttpRequest, timeoutMsg: String): Future[HttpResponse] = {
    val response =
      if (settings.secure)
        http.singleRequest(request, clientSslContext)
      else
        http.singleRequest(request)

    // make sure we always consume response body (in case of timeout)
    val strictResponse = response.flatMap(_.toStrict(settings.bodyReadTimeout))

    val timeout = after(settings.apiServerRequestTimeout, using = system.scheduler)(
      Future.failed(new LeaseTimeoutException(s"$timeoutMsg. Is the API server up?")))

    Future.firstCompletedOf(Seq(strictResponse, timeout))
  }

  private def toLeaseResource(lcr: LeaseCustomResource) = {
    log.debug("Converting {}", lcr)
    require(
      lcr.metadata.resourceVersion.isDefined,
      s"LeaseCustomResource returned from Kubernetes without a resourceVersion: $lcr")
    val owner = lcr.spec.owner match {
      case null | "" => None
      case other     => Some(other)
    }
    LeaseResource(owner, lcr.metadata.resourceVersion.get, lcr.spec.time)
  }

  private def createLeaseResource(name: String): Future[Option[LeaseResource]] = {
    val lcr = LeaseCustomResource(Metadata(name, None), Spec("", System.currentTimeMillis()))
    for {
      entity <- Marshal(lcr).to[RequestEntity]
      response <- makeRequest(
        requestForPath(pathForLease(name), HttpMethods.POST, entity = entity),
        s"Timed out creating lease $name")
      responseEntity <- response.entity.toStrict(settings.bodyReadTimeout)
      lr <- response.status match {
        case StatusCodes.Created =>
          log.debug("lease resource created")
          Unmarshal(responseEntity).to[LeaseCustomResource].map(lcr => Some(toLeaseResource(lcr)))
        case StatusCodes.Conflict =>
          log.debug("creation of lease resource failed as already exists. Will attempt to read again")
          entity.discardBytes()
          // someone else has created it
          Future.successful(None)
        case StatusCodes.Unauthorized =>
          handleUnauthorized(response)
        case unexpected =>
          responseEntity
            .toStrict(settings.bodyReadTimeout)
            .flatMap(e => Unmarshal(e).to[String])
            .flatMap(body => {
              Future.failed(
                new LeaseException(
                  s"Unexpected response from API server when creating Lease StatusCode: ${unexpected}. Body: ${body}"))
            })
      }
    } yield lr
  }

  /**
   * This uses blocking IO, and so should only be used to read configuration at startup.
   */
  protected def readConfigVarFromFilesystem(path: String, name: String): Option[String] = {
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
