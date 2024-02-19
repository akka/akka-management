import sbt._
import Keys._

object Dependencies {

  val Scala213 = "2.13.12"
  val Scala3 = "3.3.1"
  val CrossScalaVersions = Seq(Scala213, Scala3)

  // Align the versions in integration-test/kubernetes-api-java/pom.xml
  val AkkaVersion = "2.9.0"
  val AkkaBinaryVersion = "2.9"
  // Align the versions in integration-test/kubernetes-api-java/pom.xml
  val AkkaHttpVersion = "10.6.0"
  val AkkaHttpBinaryVersion = "10.6"

  val ScalaTestVersion = "3.2.18"
  val ScalaTestPlusJUnitVersion = ScalaTestVersion + ".0"

  val AwsSdkVersion = "1.12.661"
  val JacksonVersion = "2.15.2"
  val JacksonDatabindVersion = JacksonVersion

  val Log4j2Version = "2.22.1"

  // often called-in transitively with insecure versions of databind / core
  private val JacksonDatabind = Seq(
    "com.fasterxml.jackson.core" % "jackson-databind" % JacksonDatabindVersion
  )

  val DiscoveryKubernetesApi = Seq(
    "com.typesafe.akka" %% "akka-actor" % AkkaVersion,
    "com.typesafe.akka" %% "akka-discovery" % AkkaVersion,
    "com.typesafe.akka" %% "akka-stream" % AkkaVersion,
    "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion,
    "com.typesafe.akka" %% "akka-http-spray-json" % AkkaHttpVersion,
    "org.scalatest" %% "scalatest" % ScalaTestVersion % Test
  )

  val DiscoveryMarathonApi = Seq(
    "com.typesafe.akka" %% "akka-actor" % AkkaVersion,
    "com.typesafe.akka" %% "akka-discovery" % AkkaVersion,
    "com.typesafe.akka" %% "akka-stream" % AkkaVersion,
    "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion,
    "com.typesafe.akka" %% "akka-http-spray-json" % AkkaHttpVersion,
    "org.scalatest" %% "scalatest" % ScalaTestVersion % Test
  )

  val DiscoveryAwsApi = Seq(
      "com.typesafe.akka" %% "akka-actor" % AkkaVersion,
      "com.typesafe.akka" %% "akka-discovery" % AkkaVersion,
      "com.amazonaws" % "aws-java-sdk-ec2" % AwsSdkVersion,
      "com.amazonaws" % "aws-java-sdk-ecs" % AwsSdkVersion,
      "org.scalatest" %% "scalatest" % ScalaTestVersion % Test
    ) ++ JacksonDatabind // aws-java-sdk depends on insecure version of jackson

  val DiscoveryAwsApiAsync = Seq(
      "com.typesafe.akka" %% "akka-actor" % AkkaVersion,
      "com.typesafe.akka" %% "akka-discovery" % AkkaVersion,
      "com.typesafe.akka" %% "akka-stream" % AkkaVersion,
      "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion,
      "com.typesafe.akka" %% "akka-http-spray-json" % AkkaHttpVersion,
      ("software.amazon.awssdk" % "ecs" % "2.24.5").exclude("software.amazon.awssdk", "apache-client"),
      "org.scalatest" %% "scalatest" % ScalaTestVersion % Test
    ) ++ JacksonDatabind // aws-java-sdk depends on insecure version of jackson

  val ManagementHttp = Seq(
    "com.typesafe.akka" %% "akka-actor" % AkkaVersion,
    "com.typesafe.akka" %% "akka-stream" % AkkaVersion,
    "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion,
    "com.typesafe.akka" %% "akka-http-spray-json" % AkkaHttpVersion,
    "com.typesafe.akka" %% "akka-testkit" % AkkaVersion % Test,
    "com.typesafe.akka" %% "akka-cluster" % AkkaVersion % Test,
    "com.typesafe.akka" %% "akka-http-testkit" % AkkaHttpVersion % Test,
    "org.scalatest" %% "scalatest" % ScalaTestVersion % Test,
    "org.scalatestplus" %% "junit-4-13" % ScalaTestPlusJUnitVersion % Test
  )

  val ManagementPki = Seq(
    "com.typesafe.akka" %% "akka-pki" % AkkaVersion,
    "org.scalatest" %% "scalatest" % ScalaTestVersion % Test,
    "org.scalatestplus" %% "junit-4-13" % ScalaTestPlusJUnitVersion % Test
  )

  val LoglevelsLogback = Seq(
    "com.typesafe.akka" %% "akka-actor" % AkkaVersion,
    "com.typesafe.akka" %% "akka-slf4j" % AkkaVersion,
    "com.typesafe.akka" %% "akka-stream" % AkkaVersion,
    "ch.qos.logback" % "logback-classic" % "1.2.13",
    "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion,
    "com.typesafe.akka" %% "akka-http-spray-json" % AkkaHttpVersion,
    "org.scalatest" %% "scalatest" % ScalaTestVersion % Test,
    "com.typesafe.akka" %% "akka-testkit" % AkkaVersion % Test,
    "com.typesafe.akka" %% "akka-http-testkit" % AkkaHttpVersion % Test
  )

  val LoglevelsLog4j2 = Seq(
    "com.typesafe.akka" %% "akka-actor" % AkkaVersion,
    "com.typesafe.akka" %% "akka-slf4j" % AkkaVersion,
    "com.typesafe.akka" %% "akka-stream" % AkkaVersion,
    "org.apache.logging.log4j" % "log4j-core" % Log4j2Version,
    "org.apache.logging.log4j" % "log4j-api" % Log4j2Version,
    "org.apache.logging.log4j" % "log4j-slf4j-impl" % Log4j2Version,
    "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion,
    "com.typesafe.akka" %% "akka-http-spray-json" % AkkaHttpVersion,
    "org.scalatest" %% "scalatest" % ScalaTestVersion % Test,
    "com.typesafe.akka" %% "akka-testkit" % AkkaVersion % Test,
    "com.typesafe.akka" %% "akka-http-testkit" % AkkaHttpVersion % Test
  )

  val ClusterHttp = Seq(
    "com.typesafe.akka" %% "akka-cluster" % AkkaVersion,
    "com.typesafe.akka" %% "akka-cluster-sharding" % AkkaVersion,
    "com.typesafe.akka" %% "akka-http-core" % AkkaHttpVersion,
    "com.typesafe.akka" %% "akka-http-spray-json" % AkkaHttpVersion,
    "com.typesafe.akka" %% "akka-testkit" % AkkaVersion % Test,
    "org.mockito" % "mockito-core" % "5.10.0" % Test,
    "com.typesafe.akka" %% "akka-http-testkit" % AkkaHttpVersion % Test,
    "com.typesafe.akka" %% "akka-distributed-data" % AkkaVersion % Test,
    "org.scalatest" %% "scalatest" % ScalaTestVersion % Test,
    "org.scalatestplus" %% "junit-4-13" % ScalaTestPlusJUnitVersion % Test
  )

  val ClusterBootstrap = Seq(
    "com.typesafe.akka" %% "akka-discovery" % AkkaVersion,
    "com.typesafe.akka" %% "akka-cluster" % AkkaVersion,
    "com.typesafe.akka" %% "akka-http-core" % AkkaHttpVersion,
    "com.typesafe.akka" %% "akka-http-spray-json" % AkkaHttpVersion,
    "com.typesafe.akka" %% "akka-testkit" % AkkaVersion % Test,
    "com.typesafe.akka" %% "akka-http-testkit" % AkkaHttpVersion % Test,
    "com.typesafe.akka" %% "akka-distributed-data" % AkkaVersion % Test,
    "org.scalatest" %% "scalatest" % ScalaTestVersion % Test,
    "org.scalatestplus" %% "junit-4-13" % ScalaTestPlusJUnitVersion % Test
  )

  val RollingUpdateKubernetes = Seq(
    "com.typesafe.akka" %% "akka-actor" % AkkaVersion,
    "com.typesafe.akka" %% "akka-cluster" % AkkaVersion,
    "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion,
    "com.typesafe.akka" %% "akka-http-spray-json" % AkkaHttpVersion,
    "com.typesafe.akka" %% "akka-testkit" % AkkaVersion % Test,
    "org.scalatest" %% "scalatest" % ScalaTestVersion % Test,
    "com.github.tomakehurst" % "wiremock-jre8" % "2.34.0" % Test,
    "org.scalatestplus" %% "junit-4-13" % ScalaTestPlusJUnitVersion % Test,
    "com.typesafe.akka" %% "akka-testkit" % AkkaVersion % Test
  )

  val RollingUpdateKubernetesIntegration = RollingUpdateKubernetes ++ Seq(
      "org.scalatest" %% "scalatest" % ScalaTestVersion % Test,
      "com.github.tomakehurst" % "wiremock-jre8" % "2.34.0" % Test,
      "org.scalatestplus" %% "junit-4-13" % ScalaTestPlusJUnitVersion % Test,
      "com.typesafe.akka" %% "akka-testkit" % AkkaVersion % Test
    )

  val LeaseKubernetes = Seq(
    "com.typesafe.akka" %% "akka-actor" % AkkaVersion,
    "com.typesafe.akka" %% "akka-coordination" % AkkaVersion,
    "com.typesafe.akka" %% "akka-stream" % AkkaVersion,
    "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion,
    "com.typesafe.akka" %% "akka-slf4j" % AkkaVersion,
    "com.typesafe.akka" %% "akka-http-spray-json" % AkkaHttpVersion,
    "com.github.tomakehurst" % "wiremock-jre8" % "2.35.1" % Test,
    "org.scalatest" %% "scalatest" % ScalaTestVersion % Test,
    "org.scalatestplus" %% "junit-4-13" % ScalaTestPlusJUnitVersion % Test,
    "com.typesafe.akka" %% "akka-testkit" % AkkaVersion % Test
  )

  val LeaseKubernetesTest = Seq(
    "org.scalatest" %% "scalatest" % ScalaTestVersion,
    "com.typesafe.akka" %% "akka-testkit" % AkkaVersion % Test
  )

  val BootstrapDemos = Seq(
    "com.typesafe.akka" %% "akka-discovery" % AkkaVersion,
    "com.typesafe.akka" %% "akka-testkit" % AkkaVersion % Test,
    "com.typesafe.akka" %% "akka-slf4j" % AkkaVersion,
    "ch.qos.logback" % "logback-classic" % "1.2.13",
    "org.scalatest" %% "scalatest" % ScalaTestVersion % Test
  )

}
