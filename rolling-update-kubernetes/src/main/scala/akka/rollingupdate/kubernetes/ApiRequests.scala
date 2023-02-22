/*
 * Copyright (C) 2017-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.rollingupdate.kubernetes

import akka.http.scaladsl.model.HttpMethods.PATCH
import akka.http.scaladsl.model.headers.Authorization
import akka.http.scaladsl.model.headers.OAuth2BearerToken
import akka.http.scaladsl.model.HttpEntity
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.MediaTypes
import akka.http.scaladsl.model.Uri
import akka.util.ByteString

import scala.collection.immutable

/**
 * INTERNAL API
 */
object ApiRequests {

  def podDeletionCost(settings: KubernetesSettings, apiToken: String, namespace: String, cost: Int): HttpRequest = {
    val path = Uri.Path.Empty / "api" / "v1" / "namespaces" / namespace / "pods" / settings.podName
    val scheme = if (settings.secure) "https" else "http"
    val uri = Uri.from(scheme, host = settings.apiServiceHost, port = settings.apiServicePort.toInt).withPath(path)
    val headers = if (settings.secure) immutable.Seq(Authorization(OAuth2BearerToken(apiToken))) else Nil

    HttpRequest(
      method = PATCH,
      uri = uri,
      headers = headers,
      entity = HttpEntity(
        MediaTypes.`application/merge-patch+json`,
        ByteString(
          s"""{"metadata": {"annotations": {"controller.kubernetes.io/pod-deletion-cost": "$cost" }}}"""
        ))
    )
  }

}
