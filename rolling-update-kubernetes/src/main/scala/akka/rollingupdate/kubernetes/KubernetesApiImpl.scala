/*
 * Copyright (C) 2017-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.rollingupdate.kubernetes

import scala.collection.immutable
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import akka.Done
import akka.actor.ActorSystem
import akka.annotation.InternalApi
import akka.dispatch.Dispatchers.DefaultBlockingDispatcherId
import akka.event.Logging
import akka.event.LoggingAdapter
import akka.http.scaladsl.ConnectionContext
import akka.http.scaladsl.Http
import akka.http.scaladsl.HttpsConnectionContext
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.HttpEntity
import akka.http.scaladsl.model.HttpMethods.PATCH
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.MediaTypes
import akka.http.scaladsl.model.StatusCodes.ClientError
import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.Authorization
import akka.http.scaladsl.model.headers.OAuth2BearerToken
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.pattern.after
import akka.pki.kubernetes.PemManagersProvider
import akka.util.ByteString

import java.nio.file.Files
import java.nio.file.Paths
import java.security.KeyStore
import java.security.SecureRandom
import javax.net.ssl.KeyManager
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import scala.util.control.NonFatal

/**
 * INTERNAL API
 */
@InternalApi private[akka] class KubernetesApiImpl(
    system: ActorSystem,
    settings: KubernetesSettings,
    override val namespace: String,
    apiToken: String,
    clientHttpsConnectionContext: Option[HttpsConnectionContext])
    extends KubernetesApi
    with KubernetesJsonSupport {

  import system.dispatcher

  override val revisionAnnotation = settings.revisionAnnotation

  private implicit val sys: ActorSystem = system
  private val log = Logging(system, classOf[KubernetesApiImpl])
  private val http = Http()(system)

  private val scheme = if (settings.secure) "https" else "http"
  private lazy val headers = if (settings.secure) immutable.Seq(Authorization(OAuth2BearerToken(apiToken))) else Nil

  log.debug("kubernetes access namespace: {}. Secure: {}", namespace, settings.secure)

  override def updatePodDeletionCostAnnotation(podName: String, cost: Int): Future[Done] = {
    val path = Uri.Path.Empty / "api" / "v1" / "namespaces" / namespace / "pods" / podName
    val scheme = if (settings.secure) "https" else "http"
    val uri = Uri.from(scheme, host = settings.apiServiceHost, port = settings.apiServicePort).withPath(path)
    val headers = if (settings.secure) immutable.Seq(Authorization(OAuth2BearerToken(apiToken))) else Nil

    val httpRequest = HttpRequest(
      method = PATCH,
      uri = uri,
      headers = headers,
      entity = HttpEntity(
        MediaTypes.`application/merge-patch+json`,
        ByteString(
          s"""{"metadata": {"annotations": {"controller.kubernetes.io/pod-deletion-cost": "$cost" }}}"""
        ))
    )
    val httpResponse = makeRequest(
      httpRequest,
      s"Timed out updating pod-deletion-cost annotation for pod: [$podName] with cost: [$cost]. Namespace: [$namespace]")
    httpResponse.map {
      case HttpResponse(status, _, e, _) if status.isSuccess() =>
        e.discardBytes()
        Done
      case HttpResponse(s @ ClientError(_), _, e, _) =>
        e.discardBytes()
        throw new PodCostClientException(s.toString())
      case HttpResponse(status, _, e, _) =>
        e.discardBytes()
        throw new PodCostException(s"Request failed with status=$status")
      // Can we make this exhaustive?
    }
  }

  /*
  PATH: to get all: /apis/akka.io/v1/namespaces/<namespace>/podcosts
  PATH: to get a specific one: /apis/akka.io/v1/namespaces/<namespace>/podcosts/<system-name>
  curl -v -X POST localhost:8080/apis/akka.io/v1/namespaces/<namespace>/podcosts/ -H "Content-Type: application/yaml" --data-binary "@pod-cost-example.yml"

  responds with either:
  409 Conflict Already Exists

  OR

  201 Created if it works
   */
  override def readOrCreatePodCostResource(crName: String): Future[PodCostResource] = {
    val maxTries = 5

    def loop(tries: Int = 0): Future[PodCostResource] = {
      log.debug("Trying to create PodCost {}", tries)
      for {
        oldResource <- getPodCostResource(crName)
        lr <- oldResource match {
          case Some(found) =>
            log.debug("{} already exists. Returning {}", crName, found)
            Future.successful(found)
          case None =>
            log.info("PodCost {} does not exist, creating", crName)
            createPodCostResource(crName).flatMap {
              case Some(created) => Future.successful(created)
              case None =>
                if (tries < maxTries) loop(tries + 1)
                else Future.failed(new PodCostException(s"Unable to create or read PodCost after $maxTries tries"))
            }
        }
      } yield lr
    }

    loop()
  }

  /*
curl -v -X PUT localhost:8080/apis/akka.io/v1/namespaces/<namespace>/podcosts/<system-name> --data-binary "@pod-cost-example.yml" -H "Content-Type: application/yaml"
PUTs must contain resourceVersions. Response:
409: Resource version is out of date
200 if it is updated
   */
  /**
   * Update the named resource.
   *
   * Must [[readOrCreatePodCostResource]] to first to get a resource version.
   *
   * Can return one of three things:
   *  - Future.Failure, e.g. timed out waiting for k8s api server to respond
   *  - Future.sucess[Left(resource)]: the update failed due to version not matching current in the k8s api server.
   *    In this case the current resource is returned so the version can be used for subsequent calls
   *  - Future.sucess[Right(resource)]: Returns the PodCostResource that contains the new version.
   *    The new version should be used for any subsequent calls
   */
  override def updatePodCostResource(
      crName: String,
      version: String,
      pods: immutable.Seq[PodCost]): Future[Either[PodCostResource, PodCostResource]] = {
    val cr = PodCostCustomResource(Metadata(crName, Some(version)), Spec(pods))
    for {
      entity <- Marshal(cr).to[RequestEntity]
      response <- {
        log.debug("updating {} to {}", crName, cr)
        makeRequest(
          requestForPath(pathForPodCostResource(crName), method = HttpMethods.PUT, entity),
          s"Timed out updating PodCost [$crName]. It is not known if the update happened"
        )
      }
      result <- response.status match {
        case StatusCodes.OK =>
          Unmarshal(response.entity)
            .to[PodCostCustomResource]
            .map(updatedCr => {
              log.debug("CR after update: {}", updatedCr)
              Right(toPodCostResource(updatedCr))
            })
        case StatusCodes.Conflict =>
          getPodCostResource(crName).map {
            case None =>
              throw new PodCostException(s"GET after PUT conflict did not return a PodCost [$crName]")
            case Some(cr) =>
              log.debug("PodCostResource read after conflict: {}", cr)
              Left(cr)
          }
        case StatusCodes.Unauthorized =>
          handleUnauthorized(response)
        case unexpected =>
          Unmarshal(response.entity)
            .to[String]
            .map(body =>
              throw new PodCostException(
                s"PUT for PodCost [$crName] returned unexpected status code $unexpected. Body: $body"))
      }
    } yield result
  }

  /**
   * Start a proxy in local minikube:
   * - kubectl proxy --port=8080
   * - replic_set_name=$(curl -s http://localhost:8080/api/v1/namespaces/akka-rollingupdate-demo-ns/pods | jq '.items[0].metadata.ownerReferences[0].name'| tr -d '"')
   * - revision=$(curl -s http://localhost:8080/apis/apps/v1/namespaces/akka-rollingupdate-demo-ns/replicasets/"$replic_set_name" | jq '.metadata.annotations["deployment.kubernetes.io/revision"]'| tr -d '"')
   *
   * @return
   */
  override def readRevision(): Future[String] = {
    val maxTries = 5
    def loop(tries: Int = 0): Future[Option[String]] = {

      val powOwnerRef = getPod().map(_.metadata.ownerReferences.find(_.kind == "ReplicaSet"))

      val replicaSet = powOwnerRef.flatMap {
        case Some(podOwnerRef) => getReplicaSet(podOwnerRef.name)
        case None              => Future.failed(new ReadRevisionException("No replica name found"))
      }

      val revision = replicaSet.map(_.metadata.annotations.revision)
      revision.map(Some(_)).recoverWith {
        case ex =>
          if (tries >= maxTries) {
            Future(None)
          } else {
            log.warning(s"Failed to get revision ${ex.getMessage}. Try again ($tries)")
            loop(tries + 1)
          }
      }
    }

    loop()
      .map {
        case Some(revision) =>
          log.info(s"Reading revision from Kubernetes: akka.cluster.app-version was set to $revision")
          revision
        case None => throw new ReadRevisionException(s"Not able to read revision from Kubernetes.")
      }
      .recover {
        case ex =>
          throw new ReadRevisionException(s"Error. Not able to read revision from Kubernetes: ${ex.getMessage}")
      }

  }

  private[akka] def removePodCostResource(crName: String): Future[Done] = {
    for {
      response <- makeRequest(
        requestForPath(pathForPodCostResource(crName), HttpMethods.DELETE),
        s"Timed out removing PodCost [$crName]. It is not known if the remove happened")

      result <- response.status match {
        case StatusCodes.OK =>
          log.debug("PodCost deleted [{}]", crName)
          response.discardEntityBytes()
          Future.successful(Done)
        case StatusCodes.NotFound =>
          log.debug("PodCost already deleted [{}]", crName)
          response.discardEntityBytes()
          Future.successful(Done) // already deleted
        case StatusCodes.Unauthorized =>
          handleUnauthorized(response)
        case unexpected =>
          Unmarshal(response.entity)
            .to[String]
            .map(body =>
              throw new PodCostException(
                s"Unexpected status code when deleting PodCost. Status: $unexpected. Body: $body"))
      }
    } yield result
  }

  private def getPodCostResource(crName: String): Future[Option[PodCostResource]] = {
    val fResponse = makeRequest(requestForPath(pathForPodCostResource(crName)), s"Timed out reading PodCost [$crName]")
    for {
      response <- fResponse
      entity <- response.entity.toStrict(settings.bodyReadTimeout)
      lr <- response.status match {
        case StatusCodes.OK =>
          // it exists, parse it
          log.debug("Resource [{}] exists: {}", crName, entity)
          Unmarshal(entity).to[PodCostCustomResource].map(cr => Some(toPodCostResource(cr)))
        case StatusCodes.NotFound =>
          response.discardEntityBytes()
          log.debug("Resource [{}] does not exist", crName)
          Future.successful(None)
        case StatusCodes.Unauthorized =>
          handleUnauthorized(response)
        case unexpected =>
          Unmarshal(response.entity)
            .to[String]
            .map(body =>
              throw new PodCostException(
                s"Unexpected response from API server when retrieving PodCost StatusCode: $unexpected. Body: $body"))
      }
    } yield lr
  }

  private def handleUnauthorized(response: HttpResponse) = {
    Unmarshal(response.entity)
      .to[String]
      .map(body =>
        throw new UnauthorizedException(
          "Unauthorized to communicate with Kubernetes API server. See " +
          "https://doc.akka.io/docs/akka-management/current/rolling-updates.html#role-based-access-control " +
          s"for setting up access control. Body: $body"))
  }

  private def pathForPodCostResource(crName: String): Uri.Path =
    Uri.Path.Empty / "apis" / "akka.io" / "v1" / "namespaces" / namespace / "podcosts" / crName
      .replaceAll("[^\\d\\w\\-\\.]", "")
      .toLowerCase

  private def requestForPath(
      path: Uri.Path,
      method: HttpMethod = HttpMethods.GET,
      entity: RequestEntity = HttpEntity.Empty): HttpRequest = {
    val uri = Uri.from(scheme = scheme, host = settings.apiServiceHost, port = settings.apiServicePort).withPath(path)
    HttpRequest(uri = uri, headers = headers, method = method, entity = entity)
  }

  private def makeRequest(request: HttpRequest, timeoutMsg: String): Future[HttpResponse] = {
    val response = {
      clientHttpsConnectionContext match {
        case None                         => http.singleRequest(request)
        case Some(httpsConnectionContext) => http.singleRequest(request, httpsConnectionContext)
      }
    }

    // make sure we always consume response body (in case of timeout)
    val strictResponse = response.flatMap(_.toStrict(settings.bodyReadTimeout))

    val timeout = after(settings.apiServiceRequestTimeout, using = system.scheduler)(
      Future.failed(new PodCostTimeoutException(s"$timeoutMsg. Is the API server up?")))

    Future.firstCompletedOf(Seq(strictResponse, timeout))
  }

  private def toPodCostResource(cr: PodCostCustomResource) = {
    log.debug("Converting {}", cr)
    require(
      cr.metadata.resourceVersion.isDefined,
      s"PodCostCustomResource returned from Kubernetes without a resourceVersion: $cr")
    PodCostResource(cr.metadata.resourceVersion.get, cr.spec.pods)
  }

  private def createPodCostResource(crName: String): Future[Option[PodCostResource]] = {
    val cr = PodCostCustomResource(Metadata(crName, None), Spec(pods = Vector.empty))
    for {
      entity <- Marshal(cr).to[RequestEntity]
      response <- makeRequest(
        requestForPath(pathForPodCostResource(crName), HttpMethods.POST, entity = entity),
        s"Timed out creating PodCost $crName")
      responseEntity <- response.entity.toStrict(settings.bodyReadTimeout)
      resource <- response.status match {
        case StatusCodes.Created =>
          log.debug("PodCost resource created")
          Unmarshal(responseEntity).to[PodCostCustomResource].map(cr => Some(toPodCostResource(cr)))
        case StatusCodes.Conflict =>
          log.debug("creation of PodCost resource failed as already exists. Will attempt to read again")
          entity.discardBytes()
          // someone else has created it
          Future.successful(None)
        case StatusCodes.Unauthorized =>
          handleUnauthorized(response)
        case unexpected =>
          responseEntity
            .toStrict(settings.bodyReadTimeout)
            .flatMap(e => Unmarshal(e).to[String])
            .map(body =>
              throw new PodCostException(
                s"Unexpected response from API server when creating PodCost StatusCode: $unexpected. Body: $body"))
      }
    } yield resource
  }

  private val pathForGetPod: Uri.Path =
    Uri.Path.Empty / "api" / "v1" / "namespaces" / namespace / "pods" / settings.podName

  private def pathForReplicaSet(replicaSetName: String): Uri.Path =
    Uri.Path.Empty / "apis" / "apps" / "v1" / "namespaces" / namespace / "replicasets" / replicaSetName

  private def getReplicaSet(name: String): Future[ReplicaSet] = {
    val ent = HttpEntity.Empty.withContentType(ContentTypes.`application/json`)
    val request = requestForPath(pathForReplicaSet(name), entity = ent)
    val httpResponse = makeRequest(request, s"Timeout getting replica set '$name'")
    for {
      response <- httpResponse
      responseEntity <- response.entity.toStrict(settings.bodyReadTimeout)
      replicaSet <- response.status match {
        case StatusCodes.OK =>
          Unmarshal(responseEntity).to[ReplicaSet].recover {
            case ex =>
              throw new ReplicaSetException(s"Error while parsing ReplicaSet: ${ex.getMessage}")
          }
        case StatusCodes.Unauthorized =>
          handleUnauthorized(response)
        case unexpected =>
          responseEntity
            .toStrict(settings.bodyReadTimeout)
            .flatMap(e => Unmarshal(e).to[String])
            .map(body =>
              throw new ReplicaSetException(
                s"Unexpected response from API server when getting ReplicaSet. ReplicaSet: $unexpected. Body: $body"))
      }
    } yield {
      replicaSet
    }
  }

  private def getPod(): Future[Pod] = {

    val ent = HttpEntity.Empty.withContentType(ContentTypes.`application/json`)
    val request = requestForPath(pathForGetPod, entity = ent)
    val httpResponse = makeRequest(request, "Timeout listing pods")
    for {
      response <- httpResponse
      responseEntity <- response.entity.toStrict(settings.bodyReadTimeout)
      pod <- response.status match {
        case StatusCodes.OK =>
          Unmarshal(responseEntity).to[Pod].recover {
            case ex => throw new GetPodException(s"Error while parsing Pod: ${ex.getMessage}")
          }
        case StatusCodes.Unauthorized =>
          handleUnauthorized(response)
        case unexpected =>
          Unmarshal(response.entity)
            .to[String]
            .map(body =>
              throw new GetPodException(
                s"Unexpected response from API server when retrieving Pod. StatusCode: $unexpected. Body: $body"))
      }
    } yield pod
  }

}

/**
 * INTERNAL API
 */
@InternalApi private[akka] object KubernetesApiImpl {
  def apply(log: LoggingAdapter, k8sSettings: KubernetesSettings)(implicit system: ActorSystem) = {
    implicit val blockingDispatcher: ExecutionContext = system.dispatchers.lookup(DefaultBlockingDispatcherId)
    for {
      apiToken: String <- Future {
        readConfigVarFromFilesystem(k8sSettings.apiTokenPath, "api-token", log).getOrElse("")
      }
      podNamespace: String <- Future {
        k8sSettings.namespace
          .orElse(readConfigVarFromFilesystem(k8sSettings.namespacePath, "namespace", log))
          .getOrElse("default")
      }
      httpsContext <- Future(clientHttpsConnectionContext(k8sSettings))
    } yield {
      new KubernetesApiImpl(system, k8sSettings, podNamespace, apiToken, httpsContext)
    }
  }

  /**
   * This uses blocking IO, and so should only be used to read configuration at startup from blocking dispatcher.
   */
  private def readConfigVarFromFilesystem(path: String, name: String, log: LoggingAdapter): Option[String] = {
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

  /**
   * This uses blocking IO, and so should only be used at startup from blocking dispatcher.
   */
  private def clientHttpsConnectionContext(k8sSettings: KubernetesSettings): Option[HttpsConnectionContext] = {
    if (k8sSettings.secure) {
      val certificates = PemManagersProvider.loadCertificates(k8sSettings.apiCaPath)
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
      Some(ConnectionContext.httpsClient(sslContext))
    } else
      None
  }
}
