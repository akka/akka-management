/*
 * Copyright (C) 2017 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.discovery.kubernetes

import java.net.InetAddress
import java.nio.file.Paths

import akka.actor.ActorSystem
import akka.discovery._
import akka.http.scaladsl._
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{ Authorization, OAuth2BearerToken }
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.FileIO
import com.typesafe.sslconfig.akka.AkkaSSLConfig
import com.typesafe.sslconfig.ssl.TrustStoreConfig
import scala.collection.immutable.Seq
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.util.Try

import JsonFormat._
import SimpleServiceDiscovery.{ Resolved, ResolvedTarget }
import scala.util.control.NonFatal

import akka.event.Logging

object KubernetesApiSimpleServiceDiscovery {

  /**
   * Finds relevant targets given a pod list. Note that this doesn't filter by name as it is the job of the selector
   * to do that.
   */
  private[kubernetes] def targets(podList: PodList,
                                  portName: String,
                                  podNamespace: String,
                                  podDomain: String): Seq[ResolvedTarget] =
    for {
      item <- podList.items
      if item.metadata.flatMap(_.deletionTimestamp).isEmpty
      container <- item.spec.toVector.flatMap(_.containers)
      port <- container.ports.getOrElse(Seq.empty).find(_.name.contains(portName))
      itemStatus <- item.status
      ip <- itemStatus.podIP
      host = s"${ip.replace('.', '-')}.${podNamespace}.pod.${podDomain}"
    } yield
      ResolvedTarget(
        host = host,
        port = Some(port.containerPort),
        address = Some(InetAddress.getByName(ip))
      )
}

/**
 * An alternative implementation that uses the Kubernetes API. The main advantage of this method is that it allows
 * you to define readiness/health checks that don't affect the bootstrap mechanism.
 */
class KubernetesApiSimpleServiceDiscovery(system: ActorSystem) extends SimpleServiceDiscovery {
  import akka.discovery.kubernetes.KubernetesApiSimpleServiceDiscovery._
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

  override def lookup(query: Lookup, resolveTimeout: FiniteDuration): Future[Resolved] = {
    val labelSelector = settings.podLabelSelector(query.serviceName)

    val portName = query.portName match {
      case Some(name) => name
      case None => settings.podPortName
    }
    log.info("Querying for pods with label selector: [{}]. Namespace: [{}]. Port: [{}]", labelSelector,
      settings.podNamespace, portName)

    for {
      token <- apiToken()

      request <- optionToFuture(podRequest(token, settings.podNamespace, labelSelector),
        s"Unable to form request; check Kubernetes environment (expecting env vars ${settings.apiServiceHostEnvName}, ${settings.apiServicePortEnvName})")

      response <- http.singleRequest(request, httpsContext)

      entity <- response.entity.toStrict(resolveTimeout)

      podList <- {
        log.debug("Kubernetes API entity: [{}]", entity.data.utf8String)

        val unmarshalled = Unmarshal(entity).to[PodList]

        unmarshalled.failed.foreach { t =>
          log.error(t, "Failed to unmarshal Kubernetes API response status [{}]; check RBAC settings",
            response.status.value)
        }

        unmarshalled
      }

    } yield {
      Resolved(
        serviceName = query.serviceName,
        addresses = targets(podList, portName, settings.podNamespace, settings.podDomain)
      )
    }
  }

  private def apiToken() =
    FileIO.fromPath(Paths.get(settings.apiTokenPath)).runFold("")(_ + _.utf8String).recover { case NonFatal(_) => "" }

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
