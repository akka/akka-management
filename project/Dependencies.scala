import sbt._
import Keys._

object Dependencies {

  val Scala211 = "2.11.12"
  val Scala212 = "2.12.10"
  val Scala213 = "2.13.0"

  val AkkaVersion = "2.5.27"
  val AkkaHttpVersion = "10.1.10"

  val JUnitVersion = "4.12"
  val ScalaTestVersion = "3.0.8"
  val SprayJsonVersion = "1.3.5"

  val Common = Seq(
    libraryDependencies ++= Seq(
        "org.scalatest" %% "scalatest" % ScalaTestVersion % Test // ApacheV2
      )
  )

  private object DependencyGroups {
    val AkkaActor = Seq(
      "com.typesafe.akka" %% "akka-actor" % AkkaVersion
    )

    val AkkaDiscovery = Seq(
      "com.typesafe.akka" %% "akka-discovery" % AkkaVersion
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
      "com.typesafe.akka" %% "akka-testkit" % AkkaVersion % "test",
      "junit" % "junit" % JUnitVersion % "test",
      "org.mockito" % "mockito-all" % "1.10.19" % "test" // Common Public License 1.0
    )

    val AkkaHttpTesting = Seq(
      "com.typesafe.akka" %% "akka-http-testkit" % AkkaHttpVersion % "test"
    )

    // often called-in transitively with insecure versions of databind / core
    val JacksonDatabind = Seq(
      "com.fasterxml.jackson.core" % "jackson-databind" % "2.9.7"
    )

    val ConsulClient = Seq(
        //License: Apache 2.0
        "com.orbitz.consul" % "consul-client" % "1.1.2",
        //License: Apache 2.0
        "com.pszymczyk.consul" % "embedded-consul" % "1.0.2" % "test",
        // Specifying guava dependency because older transitive dependency has security vulnerability
        //License: Apache 2.0
        "com.google.guava" % "guava" % "27.0.1-jre"
      ) ++ JacksonDatabind // consul depends on insecure version of jackson

    val AwsJavaSdkEc2Ecs = Seq(
        "com.amazonaws" % "aws-java-sdk-ec2" % "1.11.292",
        "com.amazonaws" % "aws-java-sdk-ecs" % "1.11.292"
      ) ++ JacksonDatabind // aws-java-sdk depends on insecure version of jackson

    val Aws2Ecs = Seq(
        "software.amazon.awssdk" % "ecs" % "2.3.9"
      ) ++ JacksonDatabind // aws-java-sdk depends on insecure version of jackson

    val Logging = Seq(
      "com.typesafe.akka" %% "akka-slf4j" % AkkaVersion,
      "ch.qos.logback" % "logback-classic" % "1.2.3"
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

  val BootstrapDemos = Seq(
    libraryDependencies ++= DependencyGroups.Logging ++
      DependencyGroups.AkkaDiscovery ++
      DependencyGroups.AkkaTesting
  )

}
