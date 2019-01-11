/*
 * Copyright (C) 2017-2018 Lightbend Inc. <http://www.lightbend.com>
 */

package akka.management

import com.typesafe.config.Config
import akka.util.JavaDurationConverters._

import scala.collection.JavaConverters._
import scala.collection.immutable
import scala.concurrent.duration.FiniteDuration

object HealthCheckSettings {
  def apply(config: Config): HealthCheckSettings =
    new HealthCheckSettings(
      config.getStringList("readiness-checks").asScala.toList,
      config.getStringList("liveness-checks").asScala.toList,
      config.getString("readiness-path"),
      config.getString("liveness-path"),
      config.getDuration("check-timeout").asScala
    )

  /**
   * Java API
   */
  def create(config: Config): HealthCheckSettings = apply(config)

  /**
   * Java API
   */
  def create(readinessChecks: java.util.List[String],
             livenessChecks: java.util.List[String],
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
final class HealthCheckSettings(val readinessChecks: immutable.Seq[String],
                                val livenessChecks: immutable.Seq[String],
                                val readinessPath: String,
                                val livenessPath: String,
                                val checkTimeout: FiniteDuration) {

  /**
   * Java API
   */
  def getReadinessChecks(): java.util.List[String] = readinessChecks.asJava

  /**
   * Java API
   */
  def getLivenessChecks(): java.util.List[String] = livenessChecks.asJava

  /**
   * Java API
   */
  def getCheckTimeout(): java.time.Duration = checkTimeout.asJava
}
