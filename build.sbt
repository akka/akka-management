import com.typesafe.sbt.packager.docker.{ Cmd, ExecCmd }
import sbt.Keys.parallelExecution

ThisBuild / resolvers += Resolver.jcenterRepo

// root
lazy val `akka-management-root` = project
  .in(file("."))
  .enablePlugins(ScalaUnidocPlugin)
  .disablePlugins(MimaPlugin)
  .aggregate(
    // When this aggregate is updated the list of modules in ManifestInfo.checkSameVersion
    // in AkkaManagement should also be updated
    `akka-discovery-aws-api`,
    `akka-discovery-aws-api-async`,
    `akka-discovery-consul`,
    `akka-discovery-kubernetes-api`,
    `akka-discovery-marathon-api`,
    `akka-management`,
    `akka-management-pki`,
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
    `lease-kubernetes`,
    `lease-kubernetes-int-test`,
    docs
  )
  .settings(
    publish / skip := true
  )

lazy val mimaPreviousArtifactsSet =
  mimaPreviousArtifacts := Set(
      organization.value %% name.value % previousStableVersion.value.getOrElse(
        throw new Error("Unable to determine previous version"))
    )

lazy val `akka-discovery-kubernetes-api` = project
  .in(file("discovery-kubernetes-api"))
  .enablePlugins(AutomateHeaderPlugin)
  .settings(
    name := "akka-discovery-kubernetes-api",
    organization := "com.lightbend.akka.discovery",
    libraryDependencies := Dependencies.DiscoveryKubernetesApi,
    mimaPreviousArtifactsSet
  )
  .dependsOn(`akka-management-pki`)

lazy val `akka-discovery-marathon-api` = project
  .in(file("discovery-marathon-api"))
  .enablePlugins(AutomateHeaderPlugin)
  .settings(
    name := "akka-discovery-marathon-api",
    organization := "com.lightbend.akka.discovery",
    libraryDependencies := Dependencies.DiscoveryMarathonApi,
    mimaPreviousArtifactsSet
  )

lazy val `akka-discovery-aws-api` = project
  .in(file("discovery-aws-api"))
  .enablePlugins(AutomateHeaderPlugin)
  .settings(
    name := "akka-discovery-aws-api",
    organization := "com.lightbend.akka.discovery",
    libraryDependencies := Dependencies.DiscoveryAwsApi,
    mimaPreviousArtifactsSet
  )

lazy val `akka-discovery-aws-api-async` = project
  .in(file("discovery-aws-api-async"))
  .enablePlugins(AutomateHeaderPlugin)
  .settings(
    name := "akka-discovery-aws-api-async",
    organization := "com.lightbend.akka.discovery",
    libraryDependencies := Dependencies.DiscoveryAwsApiAsync,
    mimaPreviousArtifactsSet
  )

lazy val `akka-discovery-consul` = project
  .in(file("discovery-consul"))
  .enablePlugins(AutomateHeaderPlugin)
  .settings(
    name := "akka-discovery-consul",
    organization := "com.lightbend.akka.discovery",
    libraryDependencies := Dependencies.DiscoveryConsul,
    mimaPreviousArtifactsSet
  )

// gathers all enabled routes and serves them (HTTP or otherwise)
lazy val `akka-management` = project
  .in(file("management"))
  .enablePlugins(AutomateHeaderPlugin)
  .settings(
    name := "akka-management",
    libraryDependencies := Dependencies.ManagementHttp,
    mimaPreviousArtifactsSet
  )

lazy val `akka-management-pki` = project
  .in(file("management-pki"))
  .enablePlugins(AutomateHeaderPlugin)
  .settings(
    name := "akka-management-pki",
    libraryDependencies := Dependencies.ManagementPki,
    // Don't enable mima until 1.1.1
    mimaPreviousArtifacts := Set.empty
  )

lazy val `loglevels-logback` = project
  .in(file("loglevels-logback"))
  .enablePlugins(AutomateHeaderPlugin)
  .settings(
    name := "akka-management-loglevels-logback",
    libraryDependencies := Dependencies.LoglevelsLogback,
    mimaPreviousArtifactsSet
  )
  .dependsOn(`akka-management`)

lazy val `cluster-http` = project
  .in(file("cluster-http"))
  .enablePlugins(AutomateHeaderPlugin)
  .settings(
    name := "akka-management-cluster-http",
    libraryDependencies := Dependencies.ClusterHttp,
    mimaPreviousArtifactsSet
  )
  .dependsOn(`akka-management`)

lazy val `cluster-bootstrap` = project
  .in(file("cluster-bootstrap"))
  .enablePlugins(AutomateHeaderPlugin)
  .settings(
    name := "akka-management-cluster-bootstrap",
    libraryDependencies := Dependencies.ClusterBootstrap,
    mimaPreviousArtifactsSet
  )
  .dependsOn(`akka-management`)

lazy val `lease-kubernetes` = project
  .in(file("lease-kubernetes"))
  .enablePlugins(AutomateHeaderPlugin)
  .settings(
    name := "akka-lease-kubernetes",
    libraryDependencies := Dependencies.LeaseKubernetes,
    mimaPreviousArtifactsSet
  )
  .settings(
    Defaults.itSettings
  )
  .configs(IntegrationTest)
  .dependsOn(`akka-management-pki`)

lazy val `lease-kubernetes-int-test` = project
  .in(file("lease-kubernetes-int-test"))
  .enablePlugins(JavaAppPackaging, DockerPlugin)
  .dependsOn(`lease-kubernetes`)
  .enablePlugins(AutomateHeaderPlugin)
  .disablePlugins(MimaPlugin)
  .settings(
    name := "akka-lease-kubernetes-int-test",
    publish / skip := true,
    whitesourceIgnore := true,
    libraryDependencies := Dependencies.LeaseKubernetesTest,
    version ~= (_.replace('+', '-')),
    dockerBaseImage := "openjdk:8-jre-alpine",
    dockerUpdateLatest := true,
    dockerCommands := dockerCommands.value.flatMap {
        case ExecCmd("ENTRYPOINT", args @ _*) => Seq(Cmd("ENTRYPOINT", args.mkString(" ")))
        case v                                => Seq(v)
      },
    dockerCommands ++= Seq(
        Cmd("USER", "root"),
        Cmd("RUN", "chgrp -R 0 . && chmod -R g=u ."),
        Cmd("RUN", "/sbin/apk", "add", "--no-cache", "bash", "bind-tools", "busybox-extras", "curl", "strace"),
        Cmd("RUN", "chmod +x /opt/docker/bin/akka-lease-kubernetes-int-test")
      )
  )

lazy val `integration-test-kubernetes-api` = project
  .in(file("integration-test/kubernetes-api"))
  .disablePlugins(MimaPlugin)
  .enablePlugins(AutomateHeaderPlugin)
  .settings(
    publish / skip := true,
    sources in (Compile, doc) := Seq.empty,
    whitesourceIgnore := true,
    libraryDependencies := Dependencies.BootstrapDemos
  )
  .dependsOn(`akka-management`, `cluster-http`, `cluster-bootstrap`, `akka-discovery-kubernetes-api`)

lazy val `integration-test-kubernetes-api-java` = project
  .in(file("integration-test/kubernetes-api-java"))
  .disablePlugins(MimaPlugin)
  .enablePlugins(AutomateHeaderPlugin)
  .settings(
    publish / skip := true,
    sources in (Compile, doc) := Seq.empty,
    whitesourceIgnore := true,
    libraryDependencies := Dependencies.BootstrapDemos
  )
  .dependsOn(
    `akka-management`,
    `cluster-http`,
    `cluster-bootstrap`,
    `akka-discovery-kubernetes-api`
  )

lazy val `integration-test-kubernetes-dns` = project
  .in(file("integration-test/kubernetes-dns"))
  .disablePlugins(MimaPlugin)
  .enablePlugins(AutomateHeaderPlugin)
  .settings(
    publish / skip := true,
    sources in (Compile, doc) := Seq.empty,
    whitesourceIgnore := true,
    libraryDependencies := Dependencies.BootstrapDemos
  )
  .dependsOn(
    `akka-management`,
    `cluster-http`,
    `cluster-bootstrap`
  )

lazy val `integration-test-aws-api-ec2-tag-based` = project
  .in(file("integration-test/aws-api-ec2"))
  .configs(IntegrationTest)
  .disablePlugins(MimaPlugin)
  .enablePlugins(AutomateHeaderPlugin)
  .settings(
    publish / skip := true,
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
  .disablePlugins(MimaPlugin)
  .enablePlugins(AutomateHeaderPlugin)
  .settings(
    name := "integration-test-marathon-api-docker",
    publish / skip := true,
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
  .disablePlugins(MimaPlugin)
  .enablePlugins(AutomateHeaderPlugin)
  .settings(
    publish / skip := true,
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
  .disablePlugins(MimaPlugin)
  .enablePlugins(AutomateHeaderPlugin)
  .settings(
    name := "integration-test-local",
    publish / skip := true,
    sources in (Compile, doc) := Seq.empty,
    whitesourceIgnore := true,
    libraryDependencies := Dependencies.BootstrapDemos
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
  .disablePlugins(MimaPlugin)
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
        "date.year" -> Common.currentYear,
        "project.url" -> "https://doc.akka.io/docs/akka-management/current/",
        "canonical.base_url" -> "https://doc.akka.io/docs/akka-management/current",
        "scala.binary.version" -> scalaBinaryVersion.value,
        "akka.version" -> Dependencies.AkkaVersion,
        "extref.akka.base_url" -> s"https://doc.akka.io/docs/akka/current/%s",
        "scaladoc.akka.base_url" -> s"https://doc.akka.io/api/akka/current/",
        "extref.akka-http.base_url" -> s"https://doc.akka.io/docs/akka-http/${Dependencies.AkkaHttpBinaryVersion}/%s",
        "scaladoc.akka.http.base_url" -> s"https://doc.akka.io/api/akka-http/${Dependencies.AkkaHttpBinaryVersion}/",
        "extref.akka-grpc.base_url" -> s"https://doc.akka.io/docs/akka-grpc/current/%s",
        "extref.akka-enhancements.base_url" -> s"https://doc.akka.io/docs/akka-enhancements/current/%s",
        "scaladoc.akka.management.base_url" -> s"/${(Preprocess / siteSubdirName).value}/"
      ),
    publishRsyncArtifacts += makeSite.value -> "www/",
    publishRsyncHost := "akkarepo@gustav.akka.io"
  )
