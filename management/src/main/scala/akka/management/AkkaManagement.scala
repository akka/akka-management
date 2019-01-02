/*
 * Copyright (C) 2017-2018 Lightbend Inc. <http://www.lightbend.com>
 */

package akka.management

import java.util.concurrent.atomic.AtomicReference

import scala.collection.immutable
import scala.concurrent.Future
import scala.concurrent.Promise
import scala.util.Failure
import scala.util.Success
import scala.util.Try

import akka.Done
import akka.actor.ActorSystem
import akka.actor.ExtendedActorSystem
import akka.actor.Extension
import akka.actor.ExtensionId
import akka.actor.ExtensionIdProvider
import akka.event.Logging
import akka.http.javadsl.HttpsConnectionContext
import akka.http.javadsl.server.directives.RouteAdapter
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.server.Directives.AsyncAuthenticator
import akka.http.scaladsl.server.Directives.authenticateBasicAsync
import akka.http.scaladsl.server.Directives.pathPrefix
import akka.http.scaladsl.server.Directives.rawPathPrefix
import akka.http.scaladsl.server.Directive
import akka.http.scaladsl.server.Directives
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.RouteResult
import akka.http.scaladsl.settings.ServerSettings
import akka.management.http.ManagementRouteProviderSettingsImpl
import akka.management.http.ManagementRouteProvider
import akka.management.http.ManagementRouteProviderSettings
import akka.stream.ActorMaterializer
import akka.util.ManifestInfo
import scala.collection.immutable
import scala.concurrent.{ Future, Promise }
import scala.util.{ Failure, Success, Try }

object AkkaManagement extends ExtensionId[AkkaManagement] with ExtensionIdProvider {
  override def lookup: AkkaManagement.type = AkkaManagement

  override def get(system: ActorSystem): AkkaManagement = super.get(system)

  override def createExtension(system: ExtendedActorSystem): AkkaManagement =
    new AkkaManagement()(system)
}

final class AkkaManagement(implicit system: ExtendedActorSystem) extends Extension {

  ManifestInfo(system).checkSameVersion(productName = "Akka Management",
    dependencies = List(
      "akka-discovery-consul",
      "akka-discovery-aws-api",
      "akka-discovery-marathon-api",
      "akka-discovery-aws-api-async",
      "akka-discovery-kubernetes-api",
      "akka-management",
      "akka-management-cluster-bootstrap",
      "akka-management-cluster-http"
    ),
    logWarning = true)

  private val log = Logging(system, getClass)
  val settings = new AkkaManagementSettings(system.settings.config)

  private implicit val materializer = ActorMaterializer()
  import system.dispatcher

  private val routeProviders: immutable.Seq[ManagementRouteProvider] = loadRouteProviders()

  private[this] val runningUri = new AtomicReference[Uri]

  /**
   * Set async authenticator to be used for management routes.
   *
   * Must be called BEFORE [[start()]] and [[routes()]] to take effect.
   * Do not call concurrently.
   */
  def setAsyncAuthenticator(auth: AsyncAuthenticator[String]): Unit =
    if (runningUri.get() eq null) {
      _asyncAuthenticator = Option(auth)
    } else
      throw new IllegalStateException(
          "Attempted to set authenticator AFTER start() was called, so this call has no effect! " +
          "You are running WITHOUT authentication enabled! Make sure to call setAsyncAuthenticator BEFORE calling start().")

  // FIXME API replace with config object and withs?
  private[this] var _asyncAuthenticator: Option[AsyncAuthenticator[String]] = None

  /**
   * Set the HTTP(S) context that should be used when binding the management HTTP server.
   *
   * Set this to `akka.http.[javadsl|scaladsl].HttpsConnectionContext` to bind the server using HTTPS.
   * Refer to the Akka HTTP documentation for details about configuring HTTPS for it.
   */
  def setHttpsContext(context: HttpsConnectionContext): Unit =
    if (runningUri.get() eq null) {
      _connectionContext = context.asInstanceOf[akka.http.scaladsl.ConnectionContext]
    } else
      throw new IllegalStateException(
          "Attempted to set HTTPS Context AFTER start() was called, so this call has no effect! " +
          "You are running Akka Management over PLAIN HTTP! Make sure to call `setHttpsContext` BEFORE calling `start()`.")
  private[this] var _connectionContext: akka.http.scaladsl.ConnectionContext = Http().defaultServerHttpContext

  private val bindingFuture = new AtomicReference[Future[Http.ServerBinding]]()
  private val selfUriPromise = Promise[Uri]() // TODO has to keep config as well as the Uri, so we can reject 2nd calls with diff uri

  private def providerSettings = {
    // port is on purpose never inferred from protocol, because this HTTP endpoint is not the "main" one for the app
    val protocol = if (_connectionContext.isSecure) "https" else "http"
    val selfBaseUri =
      Uri(s"$protocol://${settings.Http.Hostname}:${settings.Http.Port}${settings.Http.BasePath.fold("")("/" + _)}")
    ManagementRouteProviderSettingsImpl(selfBaseUri)
  }

  /**
   * Scala API: Get the routes for the HTTP management endpoint.
   *
   * This method can be used to embed the Akka management routes in an existing Akka HTTP server.
   */
  def routes: Try[Route] = prepareCombinedRoutes(providerSettings)

  // FIXME should `routes` return `Try` of throw IllegalArgumentException?

  /**
   * Java API: Get the routes for the HTTP management endpoint.
   *
   * This method can be used to embed the Akka management routes in an existing Akka HTTP server.
   * @throws IllegalArgumentException if routes configured for akka management
   */
  def getRoutes: akka.http.javadsl.server.Route = RouteAdapter(routes.get)

  /**
   * Start an Akka HTTP server to serve the HTTP management endpoint.
   */
  def start(): Future[Uri] = {
    // FIXME API make it accept config object that would have all the `withHttps`
    // FIXME API return CompletionStage for Java API, must use different name
    val serverBindingPromise = Promise[Http.ServerBinding]()
    if (bindingFuture.compareAndSet(null, serverBindingPromise.future)) {

      val effectiveBindHostname = settings.Http.EffectiveBindHostname
      val effectiveBindPort = settings.Http.EffectiveBindPort

      routes match {
        case Success(routes) ⇒
          // TODO instead of binding to hardcoded things here, discovery could also be used for this binding!
          // Basically: "give me the SRV host/port for the port called `akka-bootstrap`"
          // discovery.lookup("_akka-bootstrap" + ".effective-name.default").find(myaddress)
          // ----
          // FIXME -- think about the style of how we want to make these available

          log.info("Binding Akka Management (HTTP) endpoint to: {}:{}", effectiveBindHostname, effectiveBindPort)

          val serverFutureBinding =
            Http().bindAndHandle(
              RouteResult.route2HandlerFlow(routes),
              effectiveBindHostname,
              effectiveBindPort,
              connectionContext = this._connectionContext,
              settings = ServerSettings(system).withRemoteAddressHeader(true)
            )

          serverBindingPromise.completeWith(serverFutureBinding).future.flatMap { _ =>
            log.info("Bound Akka Management (HTTP) endpoint to: {}:{}", effectiveBindHostname, effectiveBindPort)
            selfUriPromise.success(providerSettings.selfBaseUri).future
          }

        case Failure(ex) ⇒
          log.warning(ex.getMessage)
          Future.failed(new IllegalArgumentException("Failed to start Akka Management HTTP endpoint.", ex))
      }
    } else selfUriPromise.future
  }

  private def prepareCombinedRoutes(providerSettings: ManagementRouteProviderSettings): Try[Route] = {
    val basePath: Directive[Unit] = {
      val pathPrefixName = settings.Http.BasePath.getOrElse("")
      if (pathPrefixName.isEmpty) rawPathPrefix(pathPrefixName) else pathPrefix(pathPrefixName)
    }

    def wrapWithAuthenticatorIfPresent(inner: Route): Route =
      _asyncAuthenticator match {
        case Some(asyncAuthenticator) ⇒
          authenticateBasicAsync[String](realm = "secured", asyncAuthenticator)(_ ⇒ inner)
        case _ ⇒ inner
      }

    val combinedRoutes = routeProviders.map { provider =>
      log.info("Including HTTP management routes for {}", Logging.simpleName(provider))
      provider.routes(providerSettings)
    }

    Try {
      if (combinedRoutes.nonEmpty) {
        basePath {
          wrapWithAuthenticatorIfPresent(Directives.concat(combinedRoutes: _*))
        }
      } else
        throw new IllegalArgumentException(
            "No routes configured for akka management! " +
            "Double check your `akka.management.http.route-providers` config.")
    }
  }

  def stop(): Future[Done] = {
    // FIXME API return CompletionStage for Java API, must use different name
    val binding = bindingFuture.get()

    if (binding == null) {
      Future.successful(Done)
    } else if (bindingFuture.compareAndSet(binding, null)) {
      val stopFuture = binding.flatMap(_.unbind()).map((_: Any) => Done)
      bindingFuture.set(null)
      stopFuture
    } else stop() // retry, CAS was not successful, someone else completed the stop()
  }

  private def loadRouteProviders(): immutable.Seq[ManagementRouteProvider] = {
    val dynamicAccess = system.dynamicAccess

    // since often the providers are akka extensions, we initialize them here as the ActorSystem would otherwise
    settings.Http.RouteProviders map { fqcn ⇒
      dynamicAccess.getObjectFor[ExtensionIdProvider](fqcn) recoverWith {
        case _ ⇒ dynamicAccess.createInstanceFor[ExtensionIdProvider](fqcn, Nil)
      } recoverWith {
        case _ ⇒
          dynamicAccess.createInstanceFor[ExtensionIdProvider](fqcn, (classOf[ExtendedActorSystem], system) :: Nil)
      } recoverWith {
        case _ ⇒
          dynamicAccess.createInstanceFor[ManagementRouteProvider](fqcn, Nil)
      } recoverWith {
        case _ ⇒
          dynamicAccess.createInstanceFor[ManagementRouteProvider](fqcn, (classOf[ExtendedActorSystem], system) :: Nil)
      } match {
        case Success(p: ExtensionIdProvider) ⇒
          val extension = system.registerExtension(p.lookup())
          extension.asInstanceOf[ManagementRouteProvider]

        case Success(p: ManagementRouteProvider) ⇒
          p

        case Success(_) ⇒
          throw new RuntimeException(s"[$fqcn] is not an 'ExtensionIdProvider' or 'ExtensionId'")

        case Failure(problem) ⇒
          throw new RuntimeException(s"While trying to load extension [$fqcn]", problem)
      }
    }
  }

}
