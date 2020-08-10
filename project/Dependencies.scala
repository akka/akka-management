import sbt._
import Keys._

object Dependencies {

  val CronBuild = sys.env.get("TRAVIS_EVENT_TYPE").contains("cron")

  val Scala211 = "2.11.12"
  val Scala212 = "2.12.10"
  val Scala213 = "2.13.0"
  val CrossScalaVersions =
    if (CronBuild) Seq(Dependencies.Scala212, Dependencies.Scala213)
    else Seq(Dependencies.Scala211, Dependencies.Scala212, Dependencies.Scala213)

  // Align the versions in integration-test/kubernetes-api-java/pom.xml
  val Akka25Version = "2.5.31"
  val Akka26Version = "2.6.5"
  val AkkaVersion = /*if (CronBuild) Akka26Version else */Akka25Version // FIXME: need to fix tests wrt artery
  val AkkaBinaryVersion = /*if (CronBuild) "2.6" else */"2.5"
  // Align the versions in integration-test/kubernetes-api-java/pom.xml
  val AkkaHttp101 = "10.1.11"
  val AkkaHttp102 = "10.2.0-RC2"
  val AkkaHttpVersion = if (CronBuild) AkkaHttp102 else AkkaHttp101
  val AkkaHttpBinaryVersion = if (CronBuild) "10.2" else "10.1"

  val ScalaTestVersion = "3.1.1"
  val ScalaTestPlusJUnitVersion = "3.1.2.0"

  val SprayJsonVersion = "1.3.5"

  val AwsSdkVersion = "1.11.837"
  val JacksonDatabindVersion = "2.10.4"

  object TestDeps {
    val scalaTest = Seq(
      "org.scalatest" %% "scalatest" % ScalaTestVersion,
      "org.scalatestplus" %% "junit-4-12" % ScalaTestPlusJUnitVersion
    )
    val akkaTestKit = "com.typesafe.akka" %% "akka-testkit" % AkkaVersion
  }

  val Common = Seq(
    libraryDependencies ++= TestDeps.scalaTest.map(_ % Test)
  )
  private object DependencyGroups {
    val AkkaActor = Seq(
      "com.typesafe.akka" %% "akka-actor" % AkkaVersion
    )

    val AkkaDiscovery = Seq(
      "com.typesafe.akka" %% "akka-discovery" % AkkaVersion
    )

    val AkkaCoordination = Seq(
      "com.typesafe.akka" %% "akka-coordination" % AkkaVersion
    )

    val AkkaHttpCore = Seq(
      "com.typesafe.akka" %% "akka-http-core" % AkkaHttpVersion,
      "com.typesafe.akka" %% "akka-http-spray-json" % AkkaHttpVersion,
      "io.spray" %% "spray-json" % SprayJsonVersion // ApacheV2
    )
    val AkkaHttp = Seq(
      "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion,
      "com.typesafe.akka" %% "akka-http-spray-json" % AkkaHttpVersion,
      "com.typesafe.akka" %% "akka-stream" % AkkaVersion,
      "com.typesafe.akka" %% "akka-actor" % AkkaVersion,
      "io.spray" %% "spray-json" % SprayJsonVersion // ApacheV2
    )

    val AkkaCluster = Seq(
      "com.typesafe.akka" %% "akka-cluster" % AkkaVersion
    )

    val AkkaSharding = Seq(
      "com.typesafe.akka" %% "akka-cluster" % AkkaVersion,
      "com.typesafe.akka" %% "akka-cluster-sharding" % AkkaVersion
    )

    val AkkaTesting = Seq(
      TestDeps.akkaTestKit % "test",
      "org.mockito" % "mockito-all" % "1.10.19" % "test" // Common Public License 1.0
    )

    val AkkaHttpTesting = Seq(
      "com.typesafe.akka" %% "akka-http-testkit" % AkkaHttpVersion % "test"
    )

    // often called-in transitively with insecure versions of databind / core
    val JacksonDatabind = Seq(
      "com.fasterxml.jackson.core" % "jackson-databind" % JacksonDatabindVersion
    )

    val ConsulClient = Seq(
        //License: Apache 2.0
        "com.orbitz.consul" % "consul-client" % "1.1.2",
        //License: Apache 2.0
        "com.pszymczyk.consul" % "embedded-consul" % "2.1.4" % "test",
        // Specifying guava dependency because older transitive dependency has security vulnerability
        //License: Apache 2.0
        "com.google.guava" % "guava" % "27.0.1-jre"
      ) ++ JacksonDatabind // consul depends on insecure version of jackson

    val AwsJavaSdkEc2Ecs = Seq(
        "com.amazonaws" % "aws-java-sdk-ec2" % AwsSdkVersion,
        "com.amazonaws" % "aws-java-sdk-ecs" % AwsSdkVersion
      ) ++ JacksonDatabind // aws-java-sdk depends on insecure version of jackson

    val Aws2Ecs = Seq(
        "software.amazon.awssdk" % "ecs" % "2.13.71"
      ) ++ JacksonDatabind // aws-java-sdk depends on insecure version of jackson

    val Logging = Seq(
      "com.typesafe.akka" %% "akka-slf4j" % AkkaVersion,
      "ch.qos.logback" % "logback-classic" % "1.2.3"
    )

    val WireMock = Seq(
      "com.github.tomakehurst" % "wiremock-jre8" % "2.27.1" % "test" // ApacheV2
    )
  }

  val DiscoveryConsul = Seq(
    libraryDependencies ++=
      DependencyGroups.AkkaActor ++
      DependencyGroups.AkkaDiscovery ++
      DependencyGroups.AkkaTesting ++
      DependencyGroups.ConsulClient ++
      DependencyGroups.Logging.map(_ % "test")
  )

  val DiscoveryKubernetesApi = Seq(
    libraryDependencies ++=
      DependencyGroups.AkkaActor ++
      DependencyGroups.AkkaDiscovery ++
      DependencyGroups.AkkaHttp
  )

  val DiscoveryMarathonApi = Seq(
    libraryDependencies ++=
      DependencyGroups.AkkaActor ++
      DependencyGroups.AkkaDiscovery ++
      DependencyGroups.AkkaHttp
  )

  val DiscoveryAwsApi = Seq(
    libraryDependencies ++=
      DependencyGroups.AkkaActor ++
      DependencyGroups.AkkaDiscovery ++
      DependencyGroups.AwsJavaSdkEc2Ecs // aws depends on insecure version
  )

  val DiscoveryAwsApiAsync = Seq(
    libraryDependencies ++=
      DependencyGroups.AkkaActor ++
      DependencyGroups.AkkaDiscovery ++
      DependencyGroups.AkkaHttp ++
      DependencyGroups.Aws2Ecs
  )

  val ManagementHttp = Seq(
    libraryDependencies ++=
      DependencyGroups.AkkaHttp ++
      DependencyGroups.AkkaTesting ++
      DependencyGroups.AkkaHttpTesting ++ Seq(
        "com.typesafe.akka" %% "akka-cluster" % AkkaVersion % "test"
      )
  )

  val LoglevelsLogback = Seq(
    libraryDependencies ++=
      DependencyGroups.Logging ++
      DependencyGroups.AkkaHttp ++
      DependencyGroups.AkkaTesting ++
      DependencyGroups.AkkaHttpTesting
  )

  val ClusterHttp = Seq(
    libraryDependencies ++=
      DependencyGroups.AkkaSharding ++
      DependencyGroups.AkkaHttpCore ++
      DependencyGroups.AkkaTesting ++
      DependencyGroups.AkkaHttpTesting ++ Seq(
        "com.typesafe.akka" %% "akka-distributed-data" % AkkaVersion % "test"
      )
  )

  val ClusterBootstrap = Seq(
    libraryDependencies ++=
      DependencyGroups.AkkaCluster ++
      DependencyGroups.AkkaDiscovery ++
      DependencyGroups.AkkaHttpCore ++
      DependencyGroups.AkkaTesting ++
      DependencyGroups.AkkaHttpTesting ++ Seq(
        "com.typesafe.akka" %% "akka-distributed-data" % AkkaVersion % "test"
      )
  )

  val LeaseKubernetes = Seq(
    libraryDependencies ++=
      DependencyGroups.AkkaHttp ++
      DependencyGroups.AkkaCoordination ++
      DependencyGroups.WireMock ++
      TestDeps.scalaTest.map(_ % "it,test") ++
      Seq(
        TestDeps.akkaTestKit % "it,test"
      )
  )

  val LeaseKubernetesTest = Seq(
    libraryDependencies ++=
      TestDeps.scalaTest
  )

  val BootstrapDemos = Seq(
    libraryDependencies ++= DependencyGroups.Logging ++
      DependencyGroups.AkkaDiscovery ++
      DependencyGroups.AkkaTesting
  )

}
