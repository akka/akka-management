import sbt._
import Keys._

object Dependencies {

  val Scala212 = "2.12.13"
  val Scala213 = "2.13.5"
  val CrossScalaVersions = Seq(Dependencies.Scala212, Dependencies.Scala213)

  // Align the versions in integration-test/kubernetes-api-java/pom.xml
  val AkkaVersion = "2.6.14"
  val AkkaBinaryVersion = "2.6"
  // Align the versions in integration-test/kubernetes-api-java/pom.xml
  val AkkaHttpVersion = "10.2.0"
  val AkkaHttpBinaryVersion = "10.2"

  val ScalaTestVersion = "3.1.4"
  val ScalaTestPlusJUnitVersion = ScalaTestVersion + ".0"

  val AwsSdkVersion = "1.12.134"
  val JacksonVersion = "2.11.4"

  // often called-in transitively with insecure versions of databind / core
  private val JacksonDatabind = Seq(
    "com.fasterxml.jackson.core" % "jackson-databind" % JacksonVersion
  )

  private val JacksonDatatype = Seq(
    "com.fasterxml.jackson.datatype" % "jackson-datatype-guava" % JacksonVersion,
    "com.fasterxml.jackson.datatype" % "jackson-datatype-jdk8" % JacksonVersion,
    // Specifying guava dependency because older transitive dependency has security vulnerability
    "com.google.guava" % "guava" % "29.0-jre"
  )

  val DiscoveryConsul = Seq(
      "com.typesafe.akka" %% "akka-actor" % AkkaVersion,
      "com.typesafe.akka" %% "akka-discovery" % AkkaVersion,
      "com.orbitz.consul" % "consul-client" % "1.5.3",
      "com.pszymczyk.consul" % "embedded-consul" % "2.2.1" % Test,
      "org.scalatest" %% "scalatest" % ScalaTestVersion % Test,
      "com.typesafe.akka" %% "akka-testkit" % AkkaVersion % Test,
      "com.typesafe.akka" %% "akka-slf4j" % AkkaVersion % Test,
      "ch.qos.logback" % "logback-classic" % "1.2.10" % Test
    ) ++ JacksonDatabind ++ JacksonDatatype // consul depends on insecure version of jackson

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
      "software.amazon.awssdk" % "ecs" % "2.17.105",
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
    "ch.qos.logback" % "logback-classic" % "1.2.10",
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
    "org.mockito" % "mockito-all" % "1.10.19" % Test,
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

  val LeaseKubernetes = Seq(
    "com.typesafe.akka" %% "akka-actor" % AkkaVersion,
    "com.typesafe.akka" %% "akka-coordination" % AkkaVersion,
    "com.typesafe.akka" %% "akka-stream" % AkkaVersion,
    "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion,
    "com.typesafe.akka" %% "akka-slf4j" % AkkaVersion,
    "com.typesafe.akka" %% "akka-http-spray-json" % AkkaHttpVersion,
    "com.github.tomakehurst" % "wiremock-jre8" % "2.32.0" % Test,
    "org.scalatest" %% "scalatest" % ScalaTestVersion % "it,test",
    "org.scalatestplus" %% "junit-4-13" % ScalaTestPlusJUnitVersion % "it,test",
    "com.typesafe.akka" %% "akka-testkit" % AkkaVersion % "it,test"
  )

  val LeaseKubernetesTest = Seq(
    "org.scalatest" %% "scalatest" % ScalaTestVersion
  )

  val BootstrapDemos = Seq(
    "com.typesafe.akka" %% "akka-discovery" % AkkaVersion,
    "com.typesafe.akka" %% "akka-testkit" % AkkaVersion % Test,
    "com.typesafe.akka" %% "akka-slf4j" % AkkaVersion,
    "ch.qos.logback" % "logback-classic" % "1.2.10",
    "org.scalatest" %% "scalatest" % ScalaTestVersion % Test
  )

}
