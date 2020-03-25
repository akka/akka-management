ThisBuild / resolvers += Resolver.jcenterRepo

// root
lazy val `akka-management-root` = project
  .in(file("."))
  .enablePlugins(ScalaUnidocPlugin)
  .disablePlugins(BintrayPlugin)
  .aggregate(
    // When this aggregate is updated the list of modules in ManifestInfo.checkSameVersion
    // in AkkaManagement should also be updated
    `akka-discovery-aws-api`,
    `akka-discovery-aws-api-async`,
    `akka-discovery-consul`,
    `akka-discovery-kubernetes-api`,
    `akka-discovery-marathon-api`,
    `akka-management`,
    `loglevels-logback`,
    `integration-test-aws-api-ec2-tag-based`,
    `integration-test-local`,
    `integration-test-aws-api-ecs`,
    `integration-test-kubernetes-api`,
    `integration-test-kubernetes-api-java`,
    `integration-test-kubernetes-dns`,
    `integration-test-marathon-api-docker`,
    `cluster-http`,
    `cluster-bootstrap`,
    docs
  )
  .settings(
    parallelExecution in GlobalScope := false,
    publish / skip := true
  )

lazy val `akka-discovery-kubernetes-api` = project
  .in(file("discovery-kubernetes-api"))
  .enablePlugins(AutomateHeaderPlugin)
  .settings(
    name := "akka-discovery-kubernetes-api",
    organization := "com.lightbend.akka.discovery",
    Dependencies.DiscoveryKubernetesApi
  )

lazy val `akka-discovery-marathon-api` = project
  .in(file("discovery-marathon-api"))
  .enablePlugins(AutomateHeaderPlugin)
  .settings(
    name := "akka-discovery-marathon-api",
    organization := "com.lightbend.akka.discovery",
    Dependencies.DiscoveryMarathonApi
  )

lazy val `akka-discovery-aws-api` = project
  .in(file("discovery-aws-api"))
  .enablePlugins(AutomateHeaderPlugin)
  .settings(
    name := "akka-discovery-aws-api",
    organization := "com.lightbend.akka.discovery",
    Dependencies.DiscoveryAwsApi
  )

lazy val `akka-discovery-aws-api-async` = project
  .in(file("discovery-aws-api-async"))
  .enablePlugins(AutomateHeaderPlugin)
  .settings(
    name := "akka-discovery-aws-api-async",
    organization := "com.lightbend.akka.discovery",
    Dependencies.DiscoveryAwsApiAsync
  )

lazy val `akka-discovery-consul` = project
  .in(file("discovery-consul"))
  .enablePlugins(AutomateHeaderPlugin)
  .settings(
    name := "akka-discovery-consul",
    organization := "com.lightbend.akka.discovery",
    Dependencies.DiscoveryConsul
  )

// gathers all enabled routes and serves them (HTTP or otherwise)
lazy val `akka-management` = project
  .in(file("management"))
  .enablePlugins(AutomateHeaderPlugin)
  .settings(
    name := "akka-management",
    Dependencies.ManagementHttp
  )

lazy val `loglevels-logback` = project
  .in(file("loglevels-logback"))
  .enablePlugins(AutomateHeaderPlugin)
  .settings(
    name := "akka-management-loglevels-logback",
    Dependencies.LoglevelsLogback
  ).dependsOn(`akka-management`)

lazy val `cluster-http` = project
  .in(file("cluster-http"))
  .enablePlugins(AutomateHeaderPlugin)
  .settings(
    name := "akka-management-cluster-http",
    Dependencies.ClusterHttp
  )
  .dependsOn(`akka-management`)

lazy val `cluster-bootstrap` = project
  .in(file("cluster-bootstrap"))
  .enablePlugins(AutomateHeaderPlugin)
  .settings(
    name := "akka-management-cluster-bootstrap",
    Dependencies.ClusterBootstrap
  )
  .dependsOn(`akka-management`)

lazy val `integration-test-kubernetes-api` = project
  .in(file("integration-test/kubernetes-api"))
  .disablePlugins(BintrayPlugin)
  .enablePlugins(AutomateHeaderPlugin)
  .settings(
    skip in publish := true,
    sources in (Compile, doc) := Seq.empty,
    whitesourceIgnore := true,
    Dependencies.BootstrapDemos
  )
  .dependsOn(
    `akka-management`,
    `cluster-http`,
    `cluster-bootstrap`,
    `akka-discovery-kubernetes-api`
  )

lazy val `integration-test-kubernetes-api-java` = project
  .in(file("integration-test/kubernetes-api-java"))
  .disablePlugins(BintrayPlugin)
  .enablePlugins(AutomateHeaderPlugin)
  .settings(
    skip in publish := true,
    sources in (Compile, doc) := Seq.empty,
    whitesourceIgnore := true,
    Dependencies.BootstrapDemos
  )
  .dependsOn(
    `akka-management`,
    `cluster-http`,
    `cluster-bootstrap`,
    `akka-discovery-kubernetes-api`
  )

lazy val `integration-test-kubernetes-dns` = project
  .in(file("integration-test/kubernetes-dns"))
  .disablePlugins(BintrayPlugin)
  .enablePlugins(AutomateHeaderPlugin)
  .settings(
    skip in publish := true,
    sources in (Compile, doc) := Seq.empty,
    whitesourceIgnore := true,
    Dependencies.BootstrapDemos
  )
  .dependsOn(
    `akka-management`,
    `cluster-http`,
    `cluster-bootstrap`
  )

lazy val `integration-test-aws-api-ec2-tag-based` = project
  .in(file("integration-test/aws-api-ec2"))
  .configs(IntegrationTest)
  .disablePlugins(BintrayPlugin)
  .enablePlugins(AutomateHeaderPlugin)
  .settings(
    skip in publish := true,
    whitesourceIgnore := true,
    sources in doc := Seq.empty,
    Defaults.itSettings
  )
  .dependsOn(
    `akka-management`,
    `cluster-http`,
    `akka-discovery-aws-api`,
    `cluster-bootstrap`
  )

lazy val `integration-test-marathon-api-docker` = project
  .in(file("integration-test/marathon-api-docker"))
  .disablePlugins(BintrayPlugin)
  .enablePlugins(AutomateHeaderPlugin)
  .settings(
    name := "integration-test-marathon-api-docker",
    skip in publish := true,
    sources in (Compile, doc) := Seq.empty,
    whitesourceIgnore := true
  )
  .dependsOn(
    `akka-management`,
    `cluster-http`,
    `cluster-bootstrap`,
    `akka-discovery-marathon-api`
  )

lazy val `integration-test-aws-api-ecs` = project
  .in(file("integration-test/aws-api-ecs"))
  .disablePlugins(BintrayPlugin)
  .enablePlugins(AutomateHeaderPlugin)
  .settings(
    skip in publish := true,
    sources in (Compile, doc) := Seq.empty,
    whitesourceIgnore := true
  )
  .dependsOn(
    `akka-management`,
    `cluster-http`,
    `cluster-bootstrap`,
    `akka-discovery-aws-api-async`
  )
  .enablePlugins(JavaAppPackaging, AshScriptPlugin, DockerPlugin)
  .settings(
    dockerBaseImage := "openjdk:10-jre-slim",
    com.typesafe.sbt.SbtNativePackager.autoImport.packageName in Docker := "ecs-integration-test-app",
    version in Docker := "1.0"
  )

lazy val `integration-test-local` = project
  .in(file("integration-test/local"))
  .disablePlugins(BintrayPlugin)
  .enablePlugins(AutomateHeaderPlugin)
  .settings(
    name := "integration-test-local",
    skip in publish := true,
    sources in (Compile, doc) := Seq.empty,
    whitesourceIgnore := true,
    Dependencies.BootstrapDemos
  )
  .dependsOn(
    `akka-management`,
    `cluster-http`,
    `cluster-bootstrap`
  )
  .enablePlugins(JavaAppPackaging, AshScriptPlugin)

lazy val docs = project
  .in(file("docs"))
  .enablePlugins(AkkaParadoxPlugin, ParadoxSitePlugin, PreprocessPlugin, PublishRsyncPlugin)
  .disablePlugins(BintrayPlugin)
  .settings(
    name := "Akka Management",
    publish / skip := true,
    whitesourceIgnore := true,
    makeSite := makeSite.dependsOn(LocalRootProject / ScalaUnidoc / doc).value,
    Preprocess / siteSubdirName := s"api/akka-management/${if (isSnapshot.value) "snapshot" else version.value}",
    Preprocess / sourceDirectory := (LocalRootProject / ScalaUnidoc / unidoc / target).value,
    Preprocess / preprocessRules := Seq(
        ("\\.java\\.scala".r, _ => ".java")
      ),
    previewPath := (Paradox / siteSubdirName).value,
    paradoxGroups := Map("Language" -> Seq("Java", "Scala")),
    Paradox / siteSubdirName := s"docs/akka-management/${if (isSnapshot.value) "snapshot" else version.value}",
    Compile / paradoxProperties ++= Map(
        "project.url" -> "https://doc.akka.io/docs/akka-management/current/",
        "canonical.base_url" -> "https://doc.akka.io/docs/akka-management/current",
        "scala.binary_version" -> scalaBinaryVersion.value,
        "akka.version" -> Dependencies.AkkaVersion,
        "extref.akka.base_url" -> s"https://doc.akka.io/docs/akka/${Dependencies.AkkaVersion}/%s",
        "scaladoc.akka.base_url" -> s"https://doc.akka.io/api/akka/${Dependencies.AkkaVersion}/",
        "extref.akka-http.base_url" -> s"https://doc.akka.io/docs/akka-http/${Dependencies.AkkaHttpVersion}/%s",
        "scaladoc.akka.http.base_url" -> s"https://doc.akka.io/api/akka-http/${Dependencies.AkkaHttpVersion}/",
        "extref.akka-grpc.base_url" -> s"https://doc.akka.io/docs/akka-grpc/current/%s",
        "extref.akka-enhancements.base_url" -> s"https://doc.akka.io/docs/akka-enhancements/current/%s",
        "scaladoc.akka.management.base_url" -> s"/${(Preprocess / siteSubdirName).value}/"
      ),
    publishRsyncArtifact := makeSite.value -> "www/",
    publishRsyncHost := "akkarepo@gustav.akka.io"
  )
