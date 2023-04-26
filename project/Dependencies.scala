import sbt._
import Keys._

object Dependencies {

  val Scala212 = "2.12.17"
  val Scala213 = "2.13.10"
  val Scala3 = "3.1.3"
  val CrossScalaVersions = Seq(Scala213, Scala212, Scala3)

  // Align the versions in integration-test/kubernetes-api-java/pom.xml
  val AkkaVersion = "2.7.0"
  val AkkaBinaryVersion = "2.7"
  // Align the versions in integration-test/kubernetes-api-java/pom.xml
  val AkkaHttpVersion = "10.5.0-M1"
  val AkkaHttpBinaryVersion = "10.5"

  val ScalaTestVersion = "3.2.15"
  val ScalaTestPlusJUnitVersion = ScalaTestVersion + ".0"

  val AwsSdkVersion = "1.12.455"
  val JacksonVersion = "2.13.4"
  val JacksonDatabindVersion = "2.13.4.2"

  val Log4j2Version = "2.20.0"

  // often called-in transitively with insecure versions of databind / core
  private val JacksonDatabind = Seq(
    "com.fasterxml.jackson.core" % "jackson-databind" % JacksonDatabindVersion
  )

  private val JacksonDatatype = Seq(
    "com.fasterxml.jackson.datatype" % "jackson-datatype-guava" % JacksonVersion,
    "com.fasterxml.jackson.datatype" % "jackson-datatype-jdk8" % JacksonVersion,
    // Specifying guava dependency because older transitive dependency has security vulnerability
    "com.google.guava" % "guava" % "31.1-jre"
  )

  val DiscoveryConsul = Seq(
      "com.typesafe.akka" %% "akka-actor" % AkkaVersion,
      "com.typesafe.akka" %% "akka-discovery" % AkkaVersion,
      "com.orbitz.consul" % "consul-client" % "1.5.3",
      "org.immutables" % "value" % "2.9.3" % Provided, // workaround for https://github.com/lampepfl/dotty/issues/13523
      "com.pszymczyk.consul" % "embedded-consul" % "2.2.1" % Test,
      "org.scalatest" %% "scalatest" % ScalaTestVersion % Test,
      "com.typesafe.akka" %% "akka-testkit" % AkkaVersion % Test,
      "com.typesafe.akka" %% "akka-slf4j" % AkkaVersion % Test,
      "ch.qos.logback" % "logback-classic" % "1.2.12" % Test
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
      ("software.amazon.awssdk" % "ecs" % "2.20.52").exclude("software.amazon.awssdk", "apache-client"),
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
    "ch.qos.logback" % "logback-classic" % "1.2.12",
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
    "org.mockito" % "mockito-core" % "4.11.0" % Test,
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
    "org.scalatest" %% "scalatest" % ScalaTestVersion % "it,test",
    "com.github.tomakehurst" % "wiremock-jre8" % "2.34.0" % Test,
    "org.scalatestplus" %% "junit-4-13" % ScalaTestPlusJUnitVersion % "test",
    "com.typesafe.akka" %% "akka-testkit" % AkkaVersion % "it,test"
  )

  val LeaseKubernetes = Seq(
    "com.typesafe.akka" %% "akka-actor" % AkkaVersion,
    "com.typesafe.akka" %% "akka-coordination" % AkkaVersion,
    "com.typesafe.akka" %% "akka-stream" % AkkaVersion,
    "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion,
    "com.typesafe.akka" %% "akka-slf4j" % AkkaVersion,
    "com.typesafe.akka" %% "akka-http-spray-json" % AkkaHttpVersion,
    "com.github.tomakehurst" % "wiremock-jre8" % "2.35.0" % Test,
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
    "ch.qos.logback" % "logback-classic" % "1.2.12",
    "org.scalatest" %% "scalatest" % ScalaTestVersion % Test
  )

}
