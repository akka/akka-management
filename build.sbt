import java.nio.file.Paths

// root
lazy val `akka-management-root` = project
  .in(file("."))
  .settings(unidocSettings)
  .enablePlugins(NoPublish)
  .disablePlugins(BintrayPlugin)
  .aggregate(
    `akka-discovery`,
    `akka-discovery-dns`,
    `akka-discovery-kubernetes-api`,
    `akka-discovery-aws-api`,
    `akka-management`,
    `cluster-http`,
    `cluster-bootstrap`,
    docs)

// interfaces and extension for Discovery
lazy val `akka-discovery` = project
  .in(file("discovery"))
  .enablePlugins(AutomateHeaderPlugin)
  .settings(unidocSettings)
  .settings(
    name := "akka-discovery",
    organization := "com.lightbend.akka.discovery",
    Dependencies.Discovery
  )

// DNS implementation of discovery, default and works well for Kubernetes among other things
lazy val `akka-discovery-dns` = project
  .in(file("discovery-dns"))
  .enablePlugins(AutomateHeaderPlugin)
  .settings(unidocSettings)
  .settings(
    name := "akka-discovery-dns",
    organization := "com.lightbend.akka.discovery",
    Dependencies.DiscoveryDns
  )
  .dependsOn(`akka-discovery`)

// K8s API implementation of discovery, allows formation to work even when readiness/health checks are failing
lazy val `akka-discovery-kubernetes-api` = project
  .in(file("discovery-kubernetes-api"))
  .enablePlugins(AutomateHeaderPlugin)
  .settings(unidocSettings)
  .settings(
    name := "akka-discovery-kubernetes-api",
    organization := "com.lightbend.akka.discovery",
    Dependencies.DiscoveryKubernetesApi
  )
  .dependsOn(`akka-discovery`)

// AWS implementation of discovery
lazy val `akka-discovery-aws-api` = project
  .in(file("discovery-aws-api"))
  .enablePlugins(AutomateHeaderPlugin)
  .settings(unidocSettings)
  .settings(
    name := "akka-discovery-aws-api",
    organization := "com.lightbend.akka.discovery",
    Dependencies.DiscoveryAwsApi
  )
  .dependsOn(`akka-discovery`)

// gathers all enabled routes and serves them (HTTP or otherwise)
lazy val `akka-management` = project
  .in(file("management"))
  .enablePlugins(AutomateHeaderPlugin)
  .settings(unidocSettings)
  .settings(
    name := "akka-management",
    Dependencies.ManagementHttp
  )

// cluster management http routes, expose information and operations about the cluster
lazy val `cluster-http` = project
  .in(file("cluster-http"))
  .enablePlugins(AutomateHeaderPlugin)
  .settings(
    name := "akka-management-cluster-http",
    Dependencies.ClusterHttp
  )
  .dependsOn(`akka-management`)

// cluster bootstraping
lazy val `cluster-bootstrap` = project
  .in(file("cluster-bootstrap"))
  .enablePlugins(AutomateHeaderPlugin)
  .settings(
    name := "akka-management-cluster-bootstrap",
    Dependencies.ClusterBootstrap
  )
  .dependsOn(`akka-management`, `akka-discovery`)

// TODO I was thinking about introducing a module called akka-management-cluster-bootstrap-dns which does not do anything,
// except pull together the 2 modules of cluster bootstrap and akka discovery dns so it's only 1 dependency you need to pick.
// I was thinking it would be nice to have "hello world bootstrap in the smallest number of steps" so that would reduce 2 deps into 1.

// demo of the bootstrap
lazy val `bootstrap-joining-demo` = project
  .in(file("bootstrap-joining-demo/kubernetes-api"))
  .enablePlugins(NoPublish)
  .disablePlugins(BintrayPlugin)
  .enablePlugins(AutomateHeaderPlugin)
  .settings(
    name := "akka-management-bootstrap-joining-demo-kubernetes-api",
    skip in publish := true,
    sources in (Compile, doc) := Seq.empty,
    whitesourceIgnore := true
  ).dependsOn(
    `akka-management`,
    `cluster-http`,
    `akka-discovery-dns`,
    `cluster-bootstrap`,
    `akka-discovery-kubernetes-api`
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
      "scaladoc.akka.management.http.base_url" -> {
        if (isSnapshot.value) Paths.get((target in paradox in Compile).value.getPath).relativize(Paths.get(unidocTask.value.head.getPath)).toString
        else s"http://developer.lightbend.com/docs/api/akka-management/${version.value}"
      },
      "scaladoc.version" -> "2.12.0"
    )
  )
