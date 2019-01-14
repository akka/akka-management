/*
 * Copyright (C) 2017-2018 Lightbend Inc. <http://www.lightbend.com>
 */

package akka.management.scaladsl

import java.util.Optional
import java.util.concurrent.CompletionStage
import java.util.function.{ Function => JFunction }

import akka.annotation.DoNotInherit
import akka.annotation.InternalApi
import akka.http.javadsl.server.directives.SecurityDirectives.ProvidedCredentials
import akka.http.scaladsl.HttpsConnectionContext
import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.server.Directives.AsyncAuthenticator
import akka.management.javadsl

object ManagementRouteProviderSettings {
  def apply(selfBaseUri: Uri): ManagementRouteProviderSettings = {
    ManagementRouteProviderSettingsImpl(selfBaseUri, None, None, None)
  }
}

/**
 * Settings object used to pass through information about the environment the routes will be running in,
 * from the component starting the actual HTTP server, to the [[ManagementRouteProvider]]
 */
@DoNotInherit sealed trait ManagementRouteProviderSettings {

  /**
   * The "self" base Uri which points to the root of the HTTP server running the route provided by the Provider.
   * Can be used to introduce some self-awareness and/or links to "self" in the routes created by the Provider.
   */
  def selfBaseUri: Uri

  /**
   * The async authenticator to be used for management routes.
   */
  def withAuth(newAuth: AsyncAuthenticator[String]): ManagementRouteProviderSettings

  def httpsConnectionContext: Option[HttpsConnectionContext]

  /**
   * The HTTPS context that should be used when binding the management HTTP server.
   *
   * Refer to the Akka HTTP documentation for details about configuring HTTPS for it.
   */
  def withHttpsConnectionContext(newHttpsConnectionContext: HttpsConnectionContext): ManagementRouteProviderSettings
}

/**
 * INTERNAL API
 */
@InternalApi private[akka] final case class ManagementRouteProviderSettingsImpl(
    override val selfBaseUri: Uri,
    scaladslAuth: Option[AsyncAuthenticator[String]],
    javadslAuth: Option[JFunction[Optional[ProvidedCredentials], CompletionStage[Optional[String]]]],
    override val httpsConnectionContext: Option[HttpsConnectionContext]
) extends ManagementRouteProviderSettings {

  // There is no public API for defining both so it should not be possible
  require(!(javadslAuth.isDefined && scaladslAuth.isDefined), "Defining both javadsl and scaladsl auth is not allowed")

  override def withAuth(newAuth: AsyncAuthenticator[String]): ManagementRouteProviderSettings =
    copy(scaladslAuth = Option(newAuth))

  override def withHttpsConnectionContext(
      newHttpsConnectionContext: HttpsConnectionContext): ManagementRouteProviderSettings =
    copy(selfBaseUri = selfBaseUri.withScheme("https"), httpsConnectionContext = Option(newHttpsConnectionContext))

  def javadslHttpsConnectionContext: Optional[akka.http.javadsl.HttpsConnectionContext] =
    httpsConnectionContext match {
      case None => Optional.empty()
      case Some(ctx) => Optional.of(ctx) // a scaladsl.HttpsConnectionContext is a javadsl.HttpsConnectionContext
    }

  def asJava: javadsl.ManagementRouteProviderSettingsImpl =
    javadsl.ManagementRouteProviderSettingsImpl(selfBaseUri = akka.http.javadsl.model.Uri.create(selfBaseUri),
      javadslAuth, scaladslAuth, javadslHttpsConnectionContext)
}
