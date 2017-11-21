import java.nio.file.Paths

lazy val `akka-management` = project
  .in(file("."))
  .settings(unidocSettings)
  .enablePlugins(NoPublish)
  .disablePlugins(BintrayPlugin)
  .aggregate(`cluster-http`, docs)

lazy val `cluster-http` = project
  .in(file("cluster-http"))
  .enablePlugins(AutomateHeaderPlugin)
  .settings(
    name := "akka-management-cluster-http",
    Dependencies.ClusterHttp
  )

lazy val `joining` = project
  .in(file("joining"))
  .enablePlugins(AutomateHeaderPlugin)
  .settings(
    name := "akka-management-joining",
    Dependencies.Joining
  )

val unidocTask = sbtunidoc.Plugin.UnidocKeys.unidoc in(ProjectRef(file("."), "akka-management"), Compile)
lazy val docs = project
  .in(file("docs"))
  .enablePlugins(ParadoxPlugin, NoPublish)
  .disablePlugins(BintrayPlugin)
  .settings(
    name := "Akka Management",
    paradoxTheme := Some(builtinParadoxTheme("generic")),
    paradox in Compile := (paradox in Compile).dependsOn(unidocTask).value,
    paradoxProperties ++= Map(
      "version" -> version.value,
      "scala.binaryVersion" -> scalaBinaryVersion.value,
      "extref.akka-docs.base_url" -> s"http://doc.akka.io/docs/akka/${Dependencies.AkkaVersion}/%s",
      "extref.akka-http-docs.base_url" -> s"http://doc.akka.io/docs/akka-http/${Dependencies.AkkaHttpVersion}/%s.html",
      "extref.java-api.base_url" -> "https://docs.oracle.com/javase/8/docs/api/index.html?%s.html",
      "scaladoc.akka.base_url" -> s"http://doc.akka.io/api/akka/${Dependencies.AkkaVersion}",
      "scaladoc.akka.cluster.http.management.base_url" -> {
        if (isSnapshot.value) Paths.get((target in paradox in Compile).value.getPath).relativize(Paths.get(unidocTask.value.head.getPath)).toString
        else s"http://developer.lightbend.com/docs/api/akka-management/${version.value}"
      },
      "scaladoc.version" -> "2.12.0"
    )
  )
