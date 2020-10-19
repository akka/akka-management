import sbt._, Keys._

import de.heikoseeberger.sbtheader._
import de.heikoseeberger.sbtheader.HeaderPlugin.autoImport._
import com.lightbend.paradox.projectinfo.ParadoxProjectInfoPluginKeys._

object Common extends AutoPlugin {

  override def trigger = allRequirements
  override def requires = plugins.JvmPlugin && HeaderPlugin

  override lazy val projectSettings =
    Seq(
      organization := "com.lightbend.akka.management",
      organizationName := "Lightbend Inc.",
      organizationHomepage := Some(url("https://www.lightbend.com/")),
      startYear := Some(2017),
      homepage := Some(url("https://akka.io/")),
      scmInfo := Some(
          ScmInfo(url("https://github.com/akka/akka-management"), "git@github.com:akka/akka-management.git")
        ),
      developers += Developer(
          "contributors",
          "Contributors",
          "https://gitter.im/akka/dev",
          url("https://github.com/akka/akka-management/graphs/contributors")
        ),
      licenses := Seq(("Apache-2.0", url("https://www.apache.org/licenses/LICENSE-2.0"))),
      description := "Akka Management is a suite of tools for operating Akka Clusters.",
      headerLicense := Some(HeaderLicense.Custom("Copyright (C) 2017-2020 Lightbend Inc. <https://www.lightbend.com>")),
      crossScalaVersions := Dependencies.CrossScalaVersions,
      projectInfoVersion := (if (isSnapshot.value) "snapshot" else version.value),
      crossVersion := CrossVersion.binary,
      scalacOptions ++= Seq(
          "-encoding",
          "UTF-8",
          "-feature",
          "-unchecked",
          "-deprecation",
          "-Xlint",
          "-Ywarn-dead-code",
          "-Xfuture",
          "-target:jvm-1.8"
        ),
      javacOptions ++= Seq(
          "-Xlint:unchecked"
        ),
      javacOptions ++= (
          if (isJdk8) Seq.empty
          else Seq("--release", "8")
        ),
      Compile / doc / scalacOptions := scalacOptions.value ++ Seq(
          "-doc-title",
          "Akka Management",
          "-doc-version",
          version.value,
          "-skip-packages",
          "akka.pattern" // for some reason Scaladoc creates this
        ),
      Compile / doc / scalacOptions ++= (scalaVersion.value match {
          case Dependencies.Scala211 =>
            Seq(
              "-doc-source-url", {
                val branch = if (isSnapshot.value) "master" else s"v${version.value}"
                s"https://github.com/akka/akka-management/tree/${branch}€{FILE_PATH}.scala#L1"
              }
            )
          case _ =>
            Seq(
              "-doc-source-url", {
                val branch = if (isSnapshot.value) "master" else s"v${version.value}"
                s"https://github.com/akka/akka-management/tree/${branch}€{FILE_PATH_EXT}#L€{FILE_LINE}"
              },
              "-doc-canonical-base-url",
              "https://doc.akka.io/api/akka-management/current/"
            )
        }),
      autoAPIMappings := true,
      // show full stack traces and test case durations
      testOptions in Test += Tests.Argument("-oDF"),
      // -v Log "test run started" / "test started" / "test run finished" events on log level "info" instead of "debug".
      // -a Show stack traces and exception class name for AssertionErrors.
      testOptions += Tests.Argument(TestFrameworks.JUnit, "-v", "-a"),
      scalaVersion := Dependencies.Scala212
    )

  private def isJdk8 =
    VersionNumber(sys.props("java.specification.version")).matchesSemVer(SemanticSelector(s"=1.8"))
}
