import com.typesafe.sbt.packager.docker.{Cmd, ExecCmd}
import sbt.Keys.parallelExecution

ThisBuild / resolvers += "Akka library repository".at("https://repo.akka.io/maven")
ThisBuild / resolvers += Resolver.jcenterRepo
// append -SNAPSHOT to version when isSnapshot
ThisBuild / dynverSonatypeSnapshots := true
Global / excludeLintKeys += autoAPIMappings
Global / excludeLintKeys += projectInfoVersion
Global / excludeLintKeys += previewPath

// root
lazy val `akka-management-root` = project
  .in(file("."))
  .enablePlugins(ScalaUnidocPlugin)
  .disablePlugins(MimaPlugin, com.geirsson.CiReleasePlugin)
  .aggregate(
    // When this aggregate is updated the list of modules in ManifestInfo.checkSameVersion
    // in AkkaManagement should also be updated
    `akka-discovery-aws-api`,
    `akka-discovery-aws-api-async`,
    `akka-discovery-azure-api`,
    `akka-discovery-kubernetes-api`,
    `akka-discovery-marathon-api`,
    `akka-management`,
    `akka-management-pki`,
    `loglevels-logback`,
    `loglevels-log4j2`,
    `cluster-http`,
    `cluster-bootstrap`,
    `rolling-update-kubernetes`,
    `lease-kubernetes`,
    docs
  )
  .settings(
    GlobalScope / parallelExecution := false,
    publish / skip := true
  )

// integration tests separated so they don't run on `test` in root project
// also, none of these are published artifacts
lazy val `akka-management-integration` = project
  .in(file("integration-test"))
  .disablePlugins(MimaPlugin, com.geirsson.CiReleasePlugin)
  .aggregate(
    `integration-test-aws-api-ec2-tag-based`,
    `integration-test-local`,
    `integration-test-aws-api-ecs`,
    `integration-test-kubernetes-api`,
    `integration-test-kubernetes-api-java`,
    `integration-test-kubernetes-dns`,
    `integration-test-marathon-api-docker`,
    `integration-test-rolling-update-kubernetes`,
    `lease-kubernetes-integration`
  )

lazy val mimaPreviousArtifactsSet =
  mimaPreviousArtifacts := Set(
      organization.value %% name.value % previousStableVersion.value.getOrElse(
        throw new Error("Unable to determine previous version"))
    )

lazy val `akka-discovery-kubernetes-api` = project
  .in(file("discovery-kubernetes-api"))
  .enablePlugins(AutomateHeaderPlugin)
  .disablePlugins(com.geirsson.CiReleasePlugin)
  .settings(
    name := "akka-discovery-kubernetes-api",
    organization := "com.lightbend.akka.discovery",
    libraryDependencies := Dependencies.DiscoveryKubernetesApi,
    mimaPreviousArtifactsSet
  )
  .dependsOn(`akka-management-pki`)

lazy val `akka-discovery-azure-api` = (project in file("akka-discovery-azure-api"))
  .enablePlugins(AutomateHeaderPlugin)
  .disablePlugins(com.geirsson.CiReleasePlugin)
  .settings(
    name := "akka-discovery-azure-api",
    organization := "com.lightbend.akka.discovery",
    libraryDependencies := Dependencies.DiscoveryAzureApi,
    mimaPreviousArtifactsSet
  )
  .dependsOn(`akka-management-pki`)

lazy val `akka-discovery-marathon-api` = project
  .in(file("discovery-marathon-api"))
  .enablePlugins(AutomateHeaderPlugin)
  .disablePlugins(com.geirsson.CiReleasePlugin)
  .settings(
    name := "akka-discovery-marathon-api",
    organization := "com.lightbend.akka.discovery",
    libraryDependencies := Dependencies.DiscoveryMarathonApi,
    mimaPreviousArtifactsSet
  )

lazy val `akka-discovery-aws-api` = project
  .in(file("discovery-aws-api"))
  .enablePlugins(AutomateHeaderPlugin)
  .disablePlugins(com.geirsson.CiReleasePlugin)
  .settings(
    name := "akka-discovery-aws-api",
    organization := "com.lightbend.akka.discovery",
    libraryDependencies := Dependencies.DiscoveryAwsApi,
    mimaPreviousArtifactsSet
  )

lazy val `akka-discovery-aws-api-async` = project
  .in(file("discovery-aws-api-async"))
  .enablePlugins(AutomateHeaderPlugin)
  .disablePlugins(com.geirsson.CiReleasePlugin)
  .settings(
    name := "akka-discovery-aws-api-async",
    organization := "com.lightbend.akka.discovery",
    libraryDependencies := Dependencies.DiscoveryAwsApiAsync,
    mimaPreviousArtifactsSet
  )

// gathers all enabled routes and serves them (HTTP or otherwise)
lazy val `akka-management` = project
  .in(file("management"))
  .enablePlugins(AutomateHeaderPlugin)
  .disablePlugins(com.geirsson.CiReleasePlugin)
  .settings(
    name := "akka-management",
    libraryDependencies := Dependencies.ManagementHttp,
    mimaPreviousArtifactsSet
  )

lazy val `akka-management-pki` = project
  .in(file("management-pki"))
  .enablePlugins(AutomateHeaderPlugin)
  .disablePlugins(com.geirsson.CiReleasePlugin)
  .settings(
    name := "akka-management-pki",
    libraryDependencies := Dependencies.ManagementPki,
    mimaPreviousArtifactsSet
  )

lazy val `loglevels-logback` = project
  .in(file("loglevels-logback"))
  .enablePlugins(AutomateHeaderPlugin)
  .disablePlugins(com.geirsson.CiReleasePlugin)
  .settings(
    name := "akka-management-loglevels-logback",
    libraryDependencies := Dependencies.LoglevelsLogback,
    mimaPreviousArtifactsSet
  )
  .dependsOn(`akka-management`)

lazy val `loglevels-log4j2` = project
  .in(file("loglevels-log4j2"))
  .enablePlugins(AutomateHeaderPlugin)
  .disablePlugins(com.geirsson.CiReleasePlugin)
  .disablePlugins(MimaPlugin)
  .settings(
    name := "akka-management-loglevels-log4j2",
    libraryDependencies := Dependencies.LoglevelsLog4j2
  )
  .dependsOn(`akka-management`)

lazy val `cluster-http` = project
  .in(file("cluster-http"))
  .enablePlugins(AutomateHeaderPlugin)
  .disablePlugins(com.geirsson.CiReleasePlugin)
  .settings(
    name := "akka-management-cluster-http",
    libraryDependencies := Dependencies.ClusterHttp,
    mimaPreviousArtifactsSet
  )
  .dependsOn(`akka-management`)

lazy val `cluster-bootstrap` = project
  .in(file("cluster-bootstrap"))
  .enablePlugins(AutomateHeaderPlugin)
  .disablePlugins(com.geirsson.CiReleasePlugin)
  .settings(
    name := "akka-management-cluster-bootstrap",
    libraryDependencies := Dependencies.ClusterBootstrap,
    mimaPreviousArtifactsSet
  )
  .dependsOn(`akka-management`)

lazy val `rolling-update-kubernetes` = project
  .in(file("rolling-update-kubernetes"))
  .enablePlugins(AutomateHeaderPlugin)
  .disablePlugins(com.geirsson.CiReleasePlugin)
  .settings(
    name := "akka-rolling-update-kubernetes",
    libraryDependencies := Dependencies.RollingUpdateKubernetes,
    mimaPreviousArtifacts := Set.empty
  )
  .dependsOn(`akka-management-pki`)

lazy val `lease-kubernetes` = project
  .in(file("lease-kubernetes"))
  .enablePlugins(AutomateHeaderPlugin)
  .disablePlugins(com.geirsson.CiReleasePlugin)
  .settings(
    name := "akka-lease-kubernetes",
    libraryDependencies := Dependencies.LeaseKubernetes,
    mimaPreviousArtifactsSet
  )
  .dependsOn(`akka-management-pki`)

lazy val `lease-kubernetes-integration` = project
  .in(file("integration-test/lease-kubernetes"))
  .enablePlugins(JavaAppPackaging, DockerPlugin)
  .disablePlugins(com.geirsson.CiReleasePlugin)
  .dependsOn(`lease-kubernetes`)
  .enablePlugins(AutomateHeaderPlugin)
  .disablePlugins(MimaPlugin)
  .settings(IntegrationTests.settings)
  .settings(
    name := "akka-lease-kubernetes-integration",
    libraryDependencies := Dependencies.LeaseKubernetesTest,
    version ~= (_.replace('+', '-')),
    dockerBaseImage := "docker.io/library/eclipse-temurin:17.0.8.1_1-jre",
    dockerUpdateLatest := true,
    dockerCommands := dockerCommands.value.flatMap {
        case ExecCmd("ENTRYPOINT", args @ _*) => Seq(Cmd("ENTRYPOINT", args.mkString(" ")))
        case v                                => Seq(v)
      },
    dockerCommands ++= Seq(
        Cmd("USER", "root"),
        Cmd("RUN", "chgrp -R 0 . && chmod -R g=u ."),
        Cmd("RUN", "chmod +x /opt/docker/bin/akka-lease-kubernetes-integration")
      )
  )

lazy val `integration-test-kubernetes-api` = project
  .in(file("integration-test/kubernetes-api"))
  .enablePlugins(AutomateHeaderPlugin)
  .disablePlugins(MimaPlugin)
  .disablePlugins(com.geirsson.CiReleasePlugin)
  .settings(IntegrationTests.settings)
  .settings(libraryDependencies := Dependencies.BootstrapDemos)
  .dependsOn(`akka-management`, `cluster-http`, `cluster-bootstrap`, `akka-discovery-kubernetes-api`)

lazy val `integration-test-kubernetes-api-java` = project
  .in(file("integration-test/kubernetes-api-java"))
  .enablePlugins(AutomateHeaderPlugin)
  .disablePlugins(MimaPlugin)
  .disablePlugins(com.geirsson.CiReleasePlugin)
  .settings(IntegrationTests.settings)
  .settings(libraryDependencies := Dependencies.BootstrapDemos)
  .dependsOn(
    `akka-management`,
    `cluster-http`,
    `cluster-bootstrap`,
    `akka-discovery-kubernetes-api`
  )

lazy val `integration-test-kubernetes-dns` = project
  .in(file("integration-test/kubernetes-dns"))
  .enablePlugins(AutomateHeaderPlugin)
  .disablePlugins(MimaPlugin)
  .disablePlugins(com.geirsson.CiReleasePlugin)
  .settings(IntegrationTests.settings)
  .settings(libraryDependencies := Dependencies.BootstrapDemos)
  .dependsOn(
    `akka-management`,
    `cluster-http`,
    `cluster-bootstrap`
  )

lazy val `integration-test-aws-api-ec2-tag-based` = project
  .in(file("integration-test/aws-api-ec2"))
  .enablePlugins(AutomateHeaderPlugin)
  .disablePlugins(MimaPlugin)
  .disablePlugins(com.geirsson.CiReleasePlugin)
  .settings(IntegrationTests.settings)
  .dependsOn(
    `akka-management`,
    `cluster-http`,
    `akka-discovery-aws-api`,
    `cluster-bootstrap`
  )

lazy val `integration-test-marathon-api-docker` = project
  .in(file("integration-test/marathon-api-docker"))
  .enablePlugins(AutomateHeaderPlugin)
  .disablePlugins(MimaPlugin)
  .disablePlugins(com.geirsson.CiReleasePlugin)
  .settings(
    name := "integration-test-marathon-api-docker"
  )
  .settings(IntegrationTests.settings)
  .dependsOn(
    `akka-management`,
    `cluster-http`,
    `cluster-bootstrap`,
    `akka-discovery-marathon-api`
  )

lazy val `integration-test-aws-api-ecs` = project
  .in(file("integration-test/aws-api-ecs"))
  .enablePlugins(AutomateHeaderPlugin)
  .disablePlugins(MimaPlugin)
  .disablePlugins(com.geirsson.CiReleasePlugin)
  .settings(IntegrationTests.settings)
  .dependsOn(
    `akka-management`,
    `cluster-http`,
    `cluster-bootstrap`,
    `akka-discovery-aws-api-async`
  )
  .enablePlugins(JavaAppPackaging, AshScriptPlugin, DockerPlugin)
  .settings(
    dockerBaseImage := "openjdk:10-jre-slim",
    Docker / com.typesafe.sbt.SbtNativePackager.autoImport.packageName := "ecs-integration-test-app",
    Docker / version := "1.0"
  )

lazy val `integration-test-local` = project
  .in(file("integration-test/local"))
  .enablePlugins(AutomateHeaderPlugin)
  .disablePlugins(MimaPlugin)
  .disablePlugins(com.geirsson.CiReleasePlugin)
  .settings(
    name := "integration-test-local",
    libraryDependencies := Dependencies.BootstrapDemos
  )
  .settings(IntegrationTests.settings)
  .dependsOn(
    `akka-management`,
    `cluster-http`,
    `cluster-bootstrap`
  )
  .enablePlugins(JavaAppPackaging, AshScriptPlugin)

lazy val `integration-test-rolling-update-kubernetes` = project
  .in(file("integration-test/rolling-update-kubernetes"))
  .enablePlugins(AutomateHeaderPlugin)
  .disablePlugins(MimaPlugin)
  .disablePlugins(com.geirsson.CiReleasePlugin)
  .settings(IntegrationTests.settings)
  .settings(
    libraryDependencies := Dependencies.BootstrapDemos ++ Dependencies.RollingUpdateKubernetesIntegration
  )
  .dependsOn(
    `akka-management`,
    `cluster-http`,
    `cluster-bootstrap`,
    `akka-discovery-kubernetes-api`,
    `rolling-update-kubernetes`
  )

lazy val docs = project
  .in(file("docs"))
  .enablePlugins(AkkaParadoxPlugin, ParadoxSitePlugin, PreprocessPlugin, PublishRsyncPlugin)
  .disablePlugins(MimaPlugin)
  .disablePlugins(com.geirsson.CiReleasePlugin)
  .settings(
    name := "Akka Management",
    publish / skip := true,
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
        "akka.binary.version" -> Dependencies.AkkaBinaryVersion,
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
