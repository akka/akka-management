/*
 * Copyright (C) 2017-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.management

import akka.annotation.ApiMayChange
import akka.annotation.InternalApi
import akka.event.LogMarker

/**
 * This is public with the purpose to document the used markers and properties of log events.
 * No guarantee that it will remain binary compatible, but the marker names and properties
 * are considered public API and will not be changed without notice.
 */
@ApiMayChange
object ManagementLogMarker {

  /**
   * INTERNAL API
   */
  @InternalApi private[akka] object Properties {
    val HttpAddress = "akkaHttpAddress"
  }

  /**
   * Marker "akkaManagementBound" of log event when Akka Management HTPP endpoint has been bound.
   * @param boundAddress The hostname and port of the bound interface. Included as property "akkaHttpAddress".
   */
  def boundHttp(boundAddress: String): LogMarker =
    LogMarker("akkaManagementBound", Map(Properties.HttpAddress -> boundAddress))

  /**
   * Marker "readinessCheckFailed" of log event when a readiness check fails.
   */
  val readinessCheckFailed: LogMarker =
    LogMarker("akkaReadinessCheckFailed")

  /**
   * Marker "akkaLivenessCheckFailed" of log event when a readiness check fails.
   */
  val livenessCheckFailed: LogMarker =
    LogMarker("akkaLivenessCheckFailed")

}
