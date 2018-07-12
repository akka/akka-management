import java.nio.file.Paths

// root
lazy val `akka-management-root` = project
  .in(file("."))
  .settings(
    unidocSettings,
    inThisBuild(List(
      resolvers += "Akka Snapshots" at "https://repo.akka.io/snapshots/"
    ))
  )
  .enablePlugins(NoPublish)
  .disablePlugins(BintrayPlugin)
  .aggregate(
    `akka-discovery`,
    `akka-discovery-aggregrate`,
    `akka-discovery-aws-api`,
    `akka-discovery-aws-api-async`,
    `akka-discovery-config`,
    `akka-discovery-consul`,
    `akka-discovery-dns`,
    `akka-discovery-kubernetes-api`,
    `akka-discovery-marathon-api`,
    `akka-management`,
    `bootstrap-joining-demo-aws-api-ec2-tag-based`,
    `bootstrap-joining-demo-aws-api-ecs`,
    `bootstrap-joining-demo-kubernetes-api`,
    `bootstrap-joining-demo-marathon-api-docker`,
    `cluster-http`,
    `cluster-bootstrap`,
    docs
  )
  .settings(
    parallelExecution in GlobalScope := false
  )

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

lazy val `akka-discovery-config` = project
  .in(file("discovery-config"))
  .enablePlugins(AutomateHeaderPlugin)
  .settings(unidocSettings)
  .settings(
    name := "akka-discovery-config",
    organization := "com.lightbend.akka.discovery",
    Dependencies.DiscoveryConfig
  )
  .dependsOn(`akka-discovery`)

lazy val `akka-discovery-aggregrate` = project
  .in(file("discovery-aggregate"))
  .enablePlugins(AutomateHeaderPlugin)
  .settings(unidocSettings)
  .settings(
    name := "akka-discovery-aggregate",
    organization := "com.lightbend.akka.discovery",
    Dependencies.DiscoveryAggregate
  )
  .dependsOn(`akka-discovery`)
  .dependsOn(`akka-discovery-config` % "test")

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

// Marathon API implementation of discovery, allows port discovery and formation to work even when readiness/health checks are failing
lazy val `akka-discovery-marathon-api` = project
  .in(file("discovery-marathon-api"))
  .enablePlugins(AutomateHeaderPlugin)
  .settings(unidocSettings)
  .settings(
    name := "akka-discovery-marathon-api",
    organization := "com.lightbend.akka.discovery",
    Dependencies.DiscoveryMarathonApi
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

// Non-blocking AWS implementation of discovery
lazy val `akka-discovery-aws-api-async` = project
  .in(file("discovery-aws-api-async"))
  .enablePlugins(AutomateHeaderPlugin)
  .settings(unidocSettings)
  .settings(
    name := "akka-discovery-aws-api-async",
    organization := "com.lightbend.akka.discovery",
    Dependencies.DiscoveryAwsApiAsync
  )
  .dependsOn(`akka-discovery`)

lazy val `akka-discovery-consul` = project
  .in(file("discovery-consul"))
  .enablePlugins(AutomateHeaderPlugin)
  .settings(unidocSettings)
  .settings(
    name := "akka-discovery-consul",
    organization := "com.lightbend.akka.discovery",
    Dependencies.DiscoveryConsul
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

// TODO: I was thinking about introducing a module called akka-management-cluster-bootstrap-dns which does not do anything,
// except pull together the 2 modules of cluster bootstrap and akka discovery dns so it's only 1 dependency you need to pick.
// I was thinking it would be nice to have "hello world bootstrap in the smallest number of steps" so that would reduce 2 deps into 1.

// demo of the bootstrap
lazy val `bootstrap-joining-demo-kubernetes-api` = project
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

lazy val `bootstrap-joining-demo-aws-api-ec2-tag-based` = project
    .in(file("bootstrap-joining-demo/aws-api-ec2"))
    .configs(IntegrationTest)
    .enablePlugins(NoPublish)
    .disablePlugins(BintrayPlugin)
    .enablePlugins(AutomateHeaderPlugin)
    .settings(
      name := "akka-management-bootstrap-joining-demo-aws-api-ec2-tag-based",
      skip in publish := true,
      whitesourceIgnore := true,
      sources in doc := Seq.empty,
      Defaults.itSettings
    ).dependsOn(
      `akka-management`,
      `cluster-http`,
      `akka-discovery-aws-api`,
      `cluster-bootstrap`
  )

lazy val `bootstrap-joining-demo-marathon-api-docker` = project
  .in(file("bootstrap-joining-demo/marathon-api-docker"))
  .enablePlugins(NoPublish)
  .disablePlugins(BintrayPlugin)
  .enablePlugins(AutomateHeaderPlugin)
  .settings(
    name := "akka-management-bootstrap-joining-demo-marathon-api-docker",
    skip in publish := true,
    sources in (Compile, doc) := Seq.empty,
    whitesourceIgnore := true
  ).dependsOn(
    `akka-management`,
    `cluster-http`,
    `akka-discovery-dns`,
    `cluster-bootstrap`,
    `akka-discovery-marathon-api`
  )

lazy val `bootstrap-joining-demo-aws-api-ecs` = project
  .in(file("bootstrap-joining-demo/aws-api-ecs"))
  .enablePlugins(NoPublish)
  .disablePlugins(BintrayPlugin)
  .enablePlugins(AutomateHeaderPlugin)
  .settings(
    name := "akka-management-bootstrap-joining-demo-aws-api-ecs",
    skip in publish := true,
    sources in (Compile, doc) := Seq.empty,
    whitesourceIgnore := true
  ).dependsOn(
    `akka-management`,
    `cluster-http`,
    `cluster-bootstrap`,
    `akka-discovery-aws-api-async`
  )
  .enablePlugins(JavaAppPackaging, AshScriptPlugin, DockerPlugin)
  .settings(
    dockerBaseImage := "openjdk:10-jre-slim",
    com.typesafe.sbt.SbtNativePackager.autoImport.packageName in Docker := "ecs-bootstrap-demo-app",
    version in Docker := "1.0"
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
