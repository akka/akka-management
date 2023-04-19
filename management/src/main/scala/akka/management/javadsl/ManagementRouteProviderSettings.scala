/*
 * Copyright (C) 2017-2023 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.management.javadsl

import java.util.Optional
import java.util.concurrent.CompletionStage
import java.util.function.{ Function => JFunction }

import akka.annotation.DoNotInherit
import akka.annotation.InternalApi
import akka.http.javadsl.HttpsConnectionContext
import akka.http.javadsl.model.Uri
import akka.http.javadsl.server.directives.SecurityDirectives.ProvidedCredentials
import akka.http.scaladsl.server.Directives.AsyncAuthenticator
import akka.management.scaladsl

object ManagementRouteProviderSettings {
  def create(selfBaseUri: Uri): ManagementRouteProviderSettings = {
    ManagementRouteProviderSettingsImpl(selfBaseUri, None, None, Optional.empty(), readOnly = true)
  }
}

/**
 * Settings object used to pass through information about the environment the routes will be running in,
 * from the component starting the actual HTTP server, to the [[ManagementRouteProvider]].
 *
 * Not for user extension.
 */
@DoNotInherit
sealed abstract class ManagementRouteProviderSettings {

  /**
   * The "self" base Uri which points to the root of the HTTP server running the route provided by the Provider.
   * Can be used to introduce some self-awareness and/or links to "self" in the routes created by the Provider.
   */
  def selfBaseUri: Uri

  /**
   * The async authenticator to be used for management routes.
   */
  def withAuth(newAuth: JFunction[Optional[ProvidedCredentials], CompletionStage[Optional[String]]])
      : ManagementRouteProviderSettings

  def httpsConnectionContext: Optional[HttpsConnectionContext]

  /**
   * The HTTPS context that should be used when binding the management HTTP server.
   *
   * Refer to the Akka HTTP documentation for details about configuring HTTPS for it.
   */
  def withHttpsConnectionContext(newHttpsConnectionContext: HttpsConnectionContext): ManagementRouteProviderSettings
  def readOnly: Boolean

  /**
   * Should only readOnly routes be provided. It is up to each provider to define what readOnly means.
   */
  def withReadOnly(readOnly: Boolean): ManagementRouteProviderSettings
}

/**
 * INTERNAL API
 */
@InternalApi private[akka] final case class ManagementRouteProviderSettingsImpl(
    override val selfBaseUri: Uri,
    javadslAuth: Option[JFunction[Optional[ProvidedCredentials], CompletionStage[Optional[String]]]],
    scaladslAuth: Option[AsyncAuthenticator[String]],
    override val httpsConnectionContext: Optional[HttpsConnectionContext],
    override val readOnly: Boolean
) extends ManagementRouteProviderSettings {

  // There is no public API for defining both so it should not be possible
  require(!(javadslAuth.isDefined && scaladslAuth.isDefined), "Defining both javadsl and scaladsl auth is not allowed")

  override def withAuth(newAuth: JFunction[Optional[ProvidedCredentials], CompletionStage[Optional[String]]])
      : ManagementRouteProviderSettings =
    copy(javadslAuth = Option(newAuth))

  override def withHttpsConnectionContext(
      newHttpsConnectionContext: HttpsConnectionContext): ManagementRouteProviderSettings =
    copy(
      selfBaseUri = selfBaseUri.scheme("https"),
      httpsConnectionContext = Optional.ofNullable(newHttpsConnectionContext))

  def scaladslHttpsConnectionContext: Option[akka.http.scaladsl.HttpsConnectionContext] = {
    if (httpsConnectionContext.isPresent) {
      httpsConnectionContext.get match {
        case ctx: akka.http.scaladsl.HttpsConnectionContext => Option(ctx)
        case other =>
          throw new IllegalStateException(
            "akka.http.javadsl.HttpsConnectionContext should be a " +
            s"akka.http.scaladsl.HttpsConnectionContext, but was [${other.getClass.getName}]")
      }
    } else {
      None
    }
  }

  override def withReadOnly(readOnly: Boolean): ManagementRouteProviderSettings = copy(readOnly = readOnly)

  def asScala: scaladsl.ManagementRouteProviderSettingsImpl =
    scaladsl.ManagementRouteProviderSettingsImpl(
      selfBaseUri = selfBaseUri.asScala,
      scaladslAuth,
      javadslAuth,
      scaladslHttpsConnectionContext,
      readOnly)
}
