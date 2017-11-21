/*
 * Copyright (C) 2017 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.cluster.http.management

import java.util.concurrent.atomic.AtomicReference

import akka.Done
import akka.actor.AddressFromURIString
import akka.cluster.bootstrap.{ ClusterBootstrap, ClusterBootstrapSettings }
import akka.cluster.bootstrap.contactpoint.HttpClusterBootstrapRoutes
import akka.cluster.sharding.{ ClusterSharding, ShardRegion }
import akka.cluster.{ Cluster, Member, MemberStatus }
import akka.event.Logging
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.{ StatusCodes, Uri }
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{ Route, RouteResult }
import akka.http.scaladsl.{ ConnectionContext, Http }
import akka.pattern.{ ask, AskTimeoutException }
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import spray.json.DefaultJsonProtocol

import scala.concurrent.duration._
import scala.concurrent.{ Future, Promise }

object ClusterHttpManagement {

  /**
   * Creates an instance of [[akka.cluster.http.management.ClusterHttpManagement]] to manage the specified
   * [[akka.cluster.Cluster]] instance. This version does not provide security (Basic Authentication or SSL)
   * and uses the default path "members".
   */
  def apply(cluster: Cluster): ClusterHttpManagement =
    new ClusterHttpManagement(cluster)

  /**
   * Creates an instance of [[akka.cluster.http.management.ClusterHttpManagement]] to manage the specified
   * [[akka.cluster.Cluster]] instance. This version does not provide security (Basic Authentication or SSL).
   * It uses the specified path `pathPrefix`.
   */
  def apply(cluster: Cluster, pathPrefix: String): ClusterHttpManagement =
    new ClusterHttpManagement(cluster, Some(pathPrefix), None, None)

  /**
   * Creates an instance of [[akka.cluster.http.management.ClusterHttpManagement]] to manage the specified
   * [[akka.cluster.Cluster]] instance. This version provides Basic Authentication with the specified
   * AsyncAuthenticator. It does not provide SSL security. It uses the default path "members".
   */
  def apply(cluster: Cluster, asyncAuthenticator: AsyncAuthenticator[String]): ClusterHttpManagement =
    new ClusterHttpManagement(cluster, None, Some(asyncAuthenticator), None)

  /**
   * Creates an instance of [[akka.cluster.http.management.ClusterHttpManagement]] to manage the specified
   * [[akka.cluster.Cluster]] instance. This version provides SSL with the specified ConnectionContext.
   * It does not provide Basic Authentication. It uses the default path "members".
   */
  def apply(cluster: Cluster, https: ConnectionContext): ClusterHttpManagement =
    new ClusterHttpManagement(cluster, None, None, Some(https))

  /**
   * Creates an instance of [[akka.cluster.http.management.ClusterHttpManagement]] to manage the specified
   * [[akka.cluster.Cluster]] instance. This version provides Basic Authentication with the specified
   * AsyncAuthenticator. It does not provide SSL security. It uses the specified path `pathPrefix`.
   */
  def apply(cluster: Cluster,
            pathPrefix: String,
            asyncAuthenticator: AsyncAuthenticator[String]): ClusterHttpManagement =
    new ClusterHttpManagement(cluster, Some(pathPrefix), Some(asyncAuthenticator), None)

  /**
   * Creates an instance of [[akka.cluster.http.management.ClusterHttpManagement]] to manage the specified
   * [[akka.cluster.Cluster]] instance. This version provides SSL with the specified ConnectionContext.
   * It does not provide Basic Authentication. It uses the specified path `pathPrefix`.
   */
  def apply(cluster: Cluster, pathPrefix: String, https: ConnectionContext) =
    new ClusterHttpManagement(cluster, Some(pathPrefix), None, Some(https))

  /**
   * Creates an instance of [[akka.cluster.http.management.ClusterHttpManagement]] to manage the specified
   * [[akka.cluster.Cluster]] instance. This version provides Basic Authentication with the specified
   * AsyncAuthenticator. It provide SSL with the specified ConnectionContext. It uses the default path "members".
   */
  def apply(cluster: Cluster,
            asyncAuthenticator: AsyncAuthenticator[String],
            https: ConnectionContext): ClusterHttpManagement =
    new ClusterHttpManagement(cluster, None, Some(asyncAuthenticator), Some(https))

  /**
   * Creates an instance of [[akka.cluster.http.management.ClusterHttpManagement]] to manage the specified
   * [[akka.cluster.Cluster]] instance. This version provides Basic Authentication with the specified
   * AsyncAuthenticator. It provide SSL with the specified ConnectionContext. It uses the specified path `pathPrefix`.
   */
  def apply(cluster: Cluster,
            pathPrefix: String,
            asyncAuthenticator: AsyncAuthenticator[String],
            https: ConnectionContext): ClusterHttpManagement =
    new ClusterHttpManagement(cluster, Some(pathPrefix), Some(asyncAuthenticator), Some(https))

  /**
   * Creates an instance of [[akka.cluster.http.management.ClusterHttpManagement]] to manage the specified
   * [[akka.cluster.Cluster]] instance. This version does not provide security (Basic Authentication or SSL)
   * and uses the default path "members".
   */
  def create(cluster: Cluster): ClusterHttpManagement =
    apply(cluster)

  /**
   * Creates an instance of [[akka.cluster.http.management.ClusterHttpManagement]] to manage the specified
   * [[akka.cluster.Cluster]] instance. This version does not provide security (Basic Authentication or SSL).
   * It uses the specified path `pathPrefix`.
   */
  def create(cluster: Cluster, pathPrefix: String): ClusterHttpManagement =
    apply(cluster, pathPrefix)

  /**
   * Creates an instance of [[akka.cluster.http.management.ClusterHttpManagement]] to manage the specified
   * [[akka.cluster.Cluster]] instance. This version provides Basic Authentication with the specified
   * AsyncAuthenticator. It does not provide SSL security. It uses the default path "members".
   */
  def create(cluster: Cluster, asyncAuthenticator: AsyncAuthenticator[String]): ClusterHttpManagement =
    apply(cluster, asyncAuthenticator)

  /**
   * Creates an instance of [[akka.cluster.http.management.ClusterHttpManagement]] to manage the specified
   * [[akka.cluster.Cluster]] instance. This version provides SSL with the specified ConnectionContext.
   * It does not provide Basic Authentication. It uses the default path "members".
   */
  def create(cluster: Cluster, https: ConnectionContext): ClusterHttpManagement =
    apply(cluster, https)

  /**
   * Creates an instance of [[akka.cluster.http.management.ClusterHttpManagement]] to manage the specified
   * [[akka.cluster.Cluster]] instance. This version provides Basic Authentication with the specified
   * AsyncAuthenticator. It does not provide SSL security. It uses the specified path `pathPrefix`.
   */
  def create(cluster: Cluster,
             pathPrefix: String,
             asyncAuthenticator: AsyncAuthenticator[String]): ClusterHttpManagement =
    apply(cluster, pathPrefix, asyncAuthenticator)

  /**
   * Creates an instance of [[akka.cluster.http.management.ClusterHttpManagement]] to manage the specified
   * [[akka.cluster.Cluster]] instance. This version provides SSL with the specified ConnectionContext.
   * It does not provide Basic Authentication. It uses the specified path `pathPrefix`.
   */
  def create(cluster: Cluster, pathPrefix: String, https: ConnectionContext) =
    apply(cluster, pathPrefix, https)

  /**
   * Creates an instance of [[akka.cluster.http.management.ClusterHttpManagement]] to manage the specified
   * [[akka.cluster.Cluster]] instance. This version provides Basic Authentication with the specified
   * AsyncAuthenticator. It provide SSL with the specified ConnectionContext. It uses the default path "members".
   */
  def create(cluster: Cluster,
             asyncAuthenticator: AsyncAuthenticator[String],
             https: ConnectionContext): ClusterHttpManagement =
    apply(cluster, asyncAuthenticator, https)

  /**
   * Creates an instance of [[akka.cluster.http.management.ClusterHttpManagement]] to manage the specified
   * [[akka.cluster.Cluster]] instance. This version provides Basic Authentication with the specified
   * AsyncAuthenticator. It provide SSL with the specified ConnectionContext. It uses the specified path `pathPrefix`.
   */
  def create(cluster: Cluster,
             pathPrefix: String,
             asyncAuthenticator: AsyncAuthenticator[String],
             https: ConnectionContext): ClusterHttpManagement =
    apply(cluster, pathPrefix, asyncAuthenticator, https)
}

/**
 * Class to instantiate an [[akka.cluster.http.management.ClusterHttpManagement]] to
 * provide an HTTP management interface for [[akka.cluster.Cluster]].
 */
class ClusterHttpManagement(
    cluster: Cluster,
    pathPrefix: Option[String] = None,
    asyncAuthenticator: Option[AsyncAuthenticator[String]] = None,
    https: Option[ConnectionContext] = None
) {

  private val settings = new ClusterHttpManagementSettings(cluster.system.settings.config)
  private implicit val system = cluster.system
  private implicit val materializer = ActorMaterializer()
  import system.dispatcher

  private val log = Logging(system, getClass)

  private val bindingFuture = new AtomicReference[Future[Http.ServerBinding]]()

  def start(): Future[Done] = {
    val serverBindingPromise = Promise[Http.ServerBinding]()
    if (bindingFuture.compareAndSet(null, serverBindingPromise.future)) {
      val clusterHttpManagementRoutes = (pathPrefix, asyncAuthenticator) match {
        case (Some(pp), Some(aa)) ⇒ ClusterHttpManagementRoutes(cluster, pp, aa)
        case (Some(pp), None) ⇒ ClusterHttpManagementRoutes(cluster, pp)
        case (None, Some(aa)) ⇒ ClusterHttpManagementRoutes(cluster, aa)
        case (None, None) ⇒ ClusterHttpManagementRoutes(cluster)
      }

      // TODO instead of binding to hardcoded things here, discovery can also be used for this binding!
      val hostname = settings.ClusterHttpManagementHostname
      val port = settings.ClusterHttpManagementPort
      // Basically: "give me the SRV host/port for the port called `akka-bootstrap`"
      // discovery.lookup("_akka-bootstrap" + ".effective-name.default").find(myaddress)
      // ----

      // FIXME -- think about the style of how we want to make these available
      // I was rather thinking that management extensions should be able to be registered somehow
      // and then be included in here
      val bootstrapConfig = ConfigFactory.parseString(s"""
        # we currently bind to the same port as akka-management and rely this information this way
        akka.cluster.bootstrap.contact-point.port-fallback = $port

        """).withFallback(system.settings.config)

      val bootstrapSettings = ClusterBootstrapSettings(bootstrapConfig)
      val bootstrapContactPointRoutes = new HttpClusterBootstrapRoutes(bootstrapSettings)
      ClusterBootstrap(system).setSelfContactPoint(Uri(s"http://$hostname:$port"))
      // FIXME -- end of fixme section

      val routes = RouteResult.route2HandlerFlow(
        clusterHttpManagementRoutes ~
        bootstrapContactPointRoutes.routes // FIXME provide a nicer way to extend akka-management routes
      )

      val serverFutureBinding =
        Http().bindAndHandle(
          routes,
          hostname,
          port,
          connectionContext = https.getOrElse(Http().defaultServerHttpContext)
        )

      log.info("Bound akka-management HTTP endpoint to: {}:{}", hostname, port)

      serverBindingPromise.completeWith(serverFutureBinding)
      serverBindingPromise.future.map(_ => Done)
    } else {
      Future.successful(Done)
    }
  }

  def stop(): Future[Done] =
    if (bindingFuture.get() == null) {
      Future.successful(Done)
    } else {
      val stopFuture = bindingFuture.get().flatMap(_.unbind()).map(_ => Done)
      bindingFuture.set(null)
      stopFuture
    }
}
