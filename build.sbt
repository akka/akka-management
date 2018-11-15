import java.nio.file.Paths

// root
lazy val `akka-management-root` = project
  .in(file("."))
  .settings(unidocSettings)
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
    `bootstrap-demo-aws-api-ec2-tag-based`,
    `bootstrap-demo-local`,
    `bootstrap-demo-aws-api-ecs`,
    `bootstrap-demo-kubernetes-api`,
    `bootstrap-demo-kubernetes-api-java`,
    `bootstrap-demo-kubernetes-dns`,
    `bootstrap-demo-marathon-api-docker`,
    `cluster-http`,
    `cluster-bootstrap`,
    docs
  )
  .settings(
    parallelExecution in GlobalScope := false,
    inThisBuild(List(
      resolvers += Resolver.sonatypeRepo("comtypesafe-2189")

    ))
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

lazy val `bootstrap-demo-kubernetes-api` = project
  .in(file("bootstrap-demo/kubernetes-api"))
  .enablePlugins(NoPublish)
  .disablePlugins(BintrayPlugin)
  .enablePlugins(AutomateHeaderPlugin)
  .settings(
    skip in publish := true,
    sources in (Compile, doc) := Seq.empty,
    whitesourceIgnore := true,
    Dependencies.BootstrapDemoKubernetesApi
  ).dependsOn(
  `akka-management`,
  `cluster-http`,
  `cluster-bootstrap`,
  `akka-discovery-kubernetes-api`
)

lazy val `bootstrap-demo-kubernetes-api-java` = project
  .in(file("bootstrap-demo/kubernetes-api-java"))
  .enablePlugins(NoPublish)
  .disablePlugins(BintrayPlugin)
  .enablePlugins(AutomateHeaderPlugin)
  .settings(
    skip in publish := true,
    sources in (Compile, doc) := Seq.empty,
    whitesourceIgnore := true,
    Dependencies.BootstrapDemoKubernetesApi
  ).dependsOn(
  `akka-management`,
  `cluster-http`,
  `cluster-bootstrap`,
  `akka-discovery-kubernetes-api`
)

lazy val `bootstrap-demo-kubernetes-dns` = project
  .in(file("bootstrap-demo/kubernetes-dns"))
  .enablePlugins(NoPublish)
  .disablePlugins(BintrayPlugin)
  .enablePlugins(AutomateHeaderPlugin)
  .settings(
    skip in publish := true,
    sources in (Compile, doc) := Seq.empty,
    whitesourceIgnore := true
  ).dependsOn(
    `akka-management`,
    `cluster-http`,
    `cluster-bootstrap`,
    `akka-discovery-dns`
  )

lazy val `bootstrap-demo-aws-api-ec2-tag-based` = project
    .in(file("bootstrap-demo/aws-api-ec2"))
    .configs(IntegrationTest)
    .enablePlugins(NoPublish)
    .disablePlugins(BintrayPlugin)
    .enablePlugins(AutomateHeaderPlugin)
    .settings(
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

lazy val `bootstrap-demo-marathon-api-docker` = project
  .in(file("bootstrap-demo/marathon-api-docker"))
  .enablePlugins(NoPublish)
  .disablePlugins(BintrayPlugin)
  .enablePlugins(AutomateHeaderPlugin)
  .settings(
    name := "akka-management-bootstrap-demo-marathon-api-docker",
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

lazy val `bootstrap-demo-aws-api-ecs` = project
  .in(file("bootstrap-demo/aws-api-ecs"))
  .enablePlugins(NoPublish)
  .disablePlugins(BintrayPlugin)
  .enablePlugins(AutomateHeaderPlugin)
  .settings(
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

lazy val `bootstrap-demo-local` = project
  .in(file("bootstrap-demo/local"))
  .enablePlugins(NoPublish)
  .disablePlugins(BintrayPlugin)
  .enablePlugins(AutomateHeaderPlugin)
  .settings(
    name := "akka-bootstrap-local",
    skip in publish := true,
    sources in (Compile, doc) := Seq.empty,
    whitesourceIgnore := true
  ).dependsOn(
    `akka-management`,
    `cluster-http`,
    `cluster-bootstrap`,
    `akka-discovery-config`
  )
  .enablePlugins(JavaAppPackaging, AshScriptPlugin)


val unidocTask = sbtunidoc.Plugin.UnidocKeys.unidoc in(ProjectRef(file("."), "akka-management"), Compile)
lazy val docs = project
  .in(file("docs"))
  .enablePlugins(ParadoxPlugin, NoPublish)
  .disablePlugins(BintrayPlugin)
  .settings(
    name := "Akka Management",
    paradoxGroups := Map("Language" -> Seq("Scala", "Java")),
    paradoxTheme := Some(builtinParadoxTheme("generic")),
    paradox in Compile := (paradox in Compile).dependsOn(unidocTask).value,
    paradoxProperties ++= Map(
      "version" -> version.value,
      "scala.binary_version" -> scalaBinaryVersion.value,
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
