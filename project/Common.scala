import com.lightbend.paradox.projectinfo.ParadoxProjectInfoPluginKeys._
import org.scalafmt.sbt.ScalafmtPlugin.autoImport._
import de.heikoseeberger.sbtheader.HeaderPlugin.autoImport._
import de.heikoseeberger.sbtheader._
import sbt.Keys._
import sbt._
import xerial.sbt.Sonatype.autoImport.sonatypeProfileName

object Common extends AutoPlugin {

  override def trigger = allRequirements
  override def requires = plugins.JvmPlugin && HeaderPlugin

  val currentYear = "2023"

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
      releaseNotesURL := (
        if (isSnapshot.value) None
        else Some(url(s"https://github.com/akka/akka-management/releases/tag/v${version.value}"))
        ),
      licenses := {
        val tagOrBranch =
          if (version.value.endsWith("SNAPSHOT")) "main"
          else "v" + version.value
        Seq(("BUSL-1.1", url(s"https://raw.githubusercontent.com/akka/akka-management/${tagOrBranch}/LICENSE")))
      },
      description := "Akka Management is a suite of tools for operating Akka Clusters.",
      headerLicense := Some(
          HeaderLicense.Custom(s"Copyright (C) 2017-$currentYear Lightbend Inc. <https://www.lightbend.com>")),
      crossScalaVersions := Dependencies.CrossScalaVersions,
      projectInfoVersion := (if (isSnapshot.value) "snapshot" else version.value),
      crossVersion := CrossVersion.binary,
      scalafmtOnCompile := System.getenv("CI") != "true",
      scalacOptions ++= {
        val scalacOptionsBase = Seq(
          "-encoding",
          "UTF-8",
          "-feature",
          "-unchecked",
          "-deprecation"
        )
        if (scalaVersion.value == Dependencies.Scala213)
          scalacOptionsBase ++: Seq("-Xlint", "-Ywarn-dead-code")
        else
          scalacOptionsBase
      },
      javacOptions ++= Seq(
          "-Xlint:unchecked"
        ),
      javacOptions ++= Seq("--release", "11"),
      scalacOptions ++= Seq("--release", "11"),
      doc / javacOptions := Nil,
      Compile / doc / scalacOptions := scalacOptions.value ++ Seq(
          "-doc-title",
          "Akka Management",
          "-doc-version",
          version.value
        ) ++
        // for some reason Scaladoc creates this
        (if (scalaVersion.value.startsWith("3")) Seq.empty
         else Seq("-skip-packages", "akka.pattern")),
      Compile / doc / scalacOptions ++= Seq(
          "-doc-source-url", {
            val branch = if (isSnapshot.value) "master" else s"v${version.value}"
            s"https://github.com/akka/akka-management/tree/${branch}€{FILE_PATH_EXT}#L€{FILE_LINE}"
          },
          "-doc-canonical-base-url",
          "https://doc.akka.io/api/akka-management/current/"
        ),
      autoAPIMappings := true,
      // show full stack traces and test case durations
      Test / testOptions += Tests.Argument("-oDF"),
      // -v Log "test run started" / "test started" / "test run finished" events on log level "info" instead of "debug".
      // -a Show stack traces and exception class name for AssertionErrors.
      testOptions += Tests.Argument(TestFrameworks.JUnit, "-v", "-a"),
      scalaVersion := Dependencies.CrossScalaVersions.head
    )

  val isJdk11orHigher: Boolean = {
    val result = VersionNumber(sys.props("java.specification.version")).matchesSemVer(SemanticSelector(">=11"))
    if (!result)
      throw new IllegalArgumentException("JDK 11 or higher is required")
    result
  }
}
