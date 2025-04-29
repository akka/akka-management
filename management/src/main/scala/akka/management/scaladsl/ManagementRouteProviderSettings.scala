/*
 * Copyright (C) 2017-2024 Lightbend Inc. <https://www.lightbend.com>
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
import akka.http.scaladsl.server.directives.Credentials
import akka.management.javadsl

import scala.compat.java8.FutureConverters.CompletionStageOps
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.jdk.OptionConverters.RichOptional

object ManagementRouteProviderSettings {
  def apply(selfBaseUri: Uri, readOnly: Boolean): ManagementRouteProviderSettings = {
    ManagementRouteProviderSettingsImpl(selfBaseUri, None, None, None, readOnly = readOnly)
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
    scaladslAuth: Option[AsyncAuthenticator[String]],
    javadslAuth: Option[JFunction[Optional[ProvidedCredentials], CompletionStage[Optional[String]]]],
    override val httpsConnectionContext: Option[HttpsConnectionContext],
    override val readOnly: Boolean
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
      case None      => Optional.empty()
      case Some(ctx) => Optional.of(ctx) // a scaladsl.HttpsConnectionContext is a javadsl.HttpsConnectionContext
    }

  override def withReadOnly(readOnly: Boolean): ManagementRouteProviderSettings = copy(readOnly = readOnly)

  def asyncAuthenticator: Option[AsyncAuthenticator[String]] =
    (scaladslAuth, javadslAuth) match {
      case (None, None)            => None
      case (scala @ Some(_), None) => scala
      case (None, Some(javaAuthenticator)) =>
        Some({ (scalaCredentials: Credentials) =>
          val javaCredentials: Optional[ProvidedCredentials] = scalaCredentials match {
            case provided: Credentials.Provided => Optional.of(ProvidedCredentials(provided))
            case _                              => Optional.empty()
          }
          javaAuthenticator.apply(javaCredentials).toScala.map(_.toScala)(ExecutionContext.parasitic)
        })

      case (Some(_), Some(_)) =>
        throw new IllegalStateException("Unexpected that both scaladsl and javadsl auth were defined")
    }

  def asJava: javadsl.ManagementRouteProviderSettingsImpl =
    javadsl.ManagementRouteProviderSettingsImpl(
      selfBaseUri = akka.http.javadsl.model.Uri.create(selfBaseUri),
      javadslAuth,
      scaladslAuth,
      javadslHttpsConnectionContext,
      readOnly)

}
