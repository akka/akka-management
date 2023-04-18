/*
 * Copyright (C) 2017-2023 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.management

import com.typesafe.config.Config
import akka.util.JavaDurationConverters._

import scala.collection.JavaConverters._
import scala.collection.immutable
import scala.concurrent.duration.FiniteDuration

final case class NamedHealthCheck(name: String, fullyQualifiedClassName: String)

object HealthCheckSettings {
  def apply(config: Config): HealthCheckSettings = {
    def validFQCN(value: Any) = {
      value != null &&
      value != "null" &&
      value.toString.trim.nonEmpty
    }

    new HealthCheckSettings(
      config
        .getConfig("readiness-checks")
        .root
        .unwrapped
        .asScala
        .collect {
          case (name, value) if validFQCN(value) => NamedHealthCheck(name, value.toString)
        }
        .toList,
      config
        .getConfig("liveness-checks")
        .root
        .unwrapped
        .asScala
        .collect {
          case (name, value) if validFQCN(value) => NamedHealthCheck(name, value.toString)
        }
        .toList,
      config.getString("readiness-path"),
      config.getString("liveness-path"),
      config.getDuration("check-timeout").asScala
    )
  }

  /**
   * Java API
   */
  def create(config: Config): HealthCheckSettings = apply(config)

  /**
   * Java API
   */
  def create(
      readinessChecks: java.util.List[NamedHealthCheck],
      livenessChecks: java.util.List[NamedHealthCheck],
      readinessPath: String,
      livenessPath: String,
      checkDuration: java.time.Duration) =
    new HealthCheckSettings(
      readinessChecks.asScala.toList,
      livenessChecks.asScala.toList,
      readinessPath,
      livenessPath,
      checkDuration.asScala
    )
}

/**
 * @param readinessChecks List of FQCN of readiness checks
 * @param livenessChecks List of FQCN of liveness checks
 * @param readinessPath The path to serve readiness on
 * @param livenessPath The path to serve liveness on
 * @param checkTimeout how long to wait for all health checks to complete
 */
final class HealthCheckSettings(
    val readinessChecks: immutable.Seq[NamedHealthCheck],
    val livenessChecks: immutable.Seq[NamedHealthCheck],
    val readinessPath: String,
    val livenessPath: String,
    val checkTimeout: FiniteDuration) {

  /**
   * Java API
   */
  def getReadinessChecks(): java.util.List[NamedHealthCheck] = readinessChecks.asJava

  /**
   * Java API
   */
  def getLivenessChecks(): java.util.List[NamedHealthCheck] = livenessChecks.asJava

  /**
   * Java API
   */
  def getCheckTimeout(): java.time.Duration = checkTimeout.asJava
}
