import sbt._, Keys._

import de.heikoseeberger.sbtheader._
import de.heikoseeberger.sbtheader.HeaderKey._
import org.scalafmt.sbt.ScalaFmtPlugin
import org.scalafmt.sbt.ScalaFmtPlugin.autoImport._

object Common extends AutoPlugin {

  val FileHeader = (HeaderPattern.cStyleBlockComment,
    """|/*
       | * Copyright (C) 2017 Lightbend Inc. <http://www.lightbend.com>
       | */
       |""".stripMargin)

  override def trigger = allRequirements
  override def requires = plugins.JvmPlugin && HeaderPlugin

  override lazy val projectSettings = reformatOnCompileSettings ++
    Dependencies.Common ++ Seq(
    organization := "com.lightbend.akka.management", // FIXME proposing this change, then we can have "com.lightbend.akka.management|discovery" etc; we'd do management.commercial for example later
    organizationName := "Lightbend Inc.",
    homepage := Some(url("https://github.com/akka/akka-management")),
    scmInfo := Some(ScmInfo(url("https://github.com/akka/akka-management"), "git@github.com:akka/akka-management.git")),
    developers += Developer("contributors", "Contributors", "https://gitter.im/akka/dev", url("https://github.com/akka/akka-management/graphs/contributors")),

    licenses := Seq(("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0"))),

    // scala versions are determined from the .travis.yml file

    crossVersion := CrossVersion.binary,

    scalacOptions ++= Seq(
      "-encoding", "UTF-8",
      "-feature",
      "-unchecked",
      "-deprecation",
      "-Xlint",
      "-Yno-adapted-args",
      "-Ywarn-dead-code",
      "-Xfuture"
    ),

    javacOptions ++= Seq(
      "-Xlint:unchecked"
    ),

    autoAPIMappings := true,

    // show full stack traces and test case durations
    testOptions in Test += Tests.Argument("-oDF"),

    // -v Log "test run started" / "test started" / "test run finished" events on log level "info" instead of "debug".
    // -a Show stack traces and exception class name for AssertionErrors.
    testOptions += Tests.Argument(TestFrameworks.JUnit, "-v", "-a"),

    headers := headers.value ++ Map(
      "scala" -> FileHeader,
      "java" -> FileHeader
    ),

    formatSbtFiles := false,
    scalafmtConfig := Some(baseDirectory.in(ThisBuild).value / ".scalafmt.conf"),

    scalaVersion := "2.12.6",
    ivyScala := ivyScala.value.map(_.copy(overrideScalaVersion = sbtPlugin.value)) // TODO Remove once this workaround no longer needed (https://github.com/sbt/sbt/issues/2786)!
  )
}
