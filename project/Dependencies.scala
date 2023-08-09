import sbt._
import Keys._

object Dependencies {

  val Scala212 = "2.12.16"
  val Scala213 = "2.13.8"
  val CrossScalaVersions = Seq(Dependencies.Scala212, Dependencies.Scala213)

  // Align the versions in integration-test/kubernetes-api-java/pom.xml
  val AkkaVersion = "2.6.14"
  // Align the versions in integration-test/kubernetes-api-java/pom.xml
  val AkkaHttpVersion = "10.2.7"

  val ScalaTestVersion = "3.1.4"
  val ScalaTestPlusJUnitVersion = ScalaTestVersion + ".0"

  val ClusterBootstrap = Seq(
    "com.typesafe.akka" %% "akka-discovery" % AkkaVersion,
    "com.typesafe.akka" %% "akka-cluster" % AkkaVersion,
    "com.typesafe.akka" %% "akka-http-core" % AkkaHttpVersion,
    "com.typesafe.akka" %% "akka-http-spray-json" % AkkaHttpVersion,
    // Making it so our modified cluster-bootstrap depends on the official implementation of akka-management core
    "com.lightbend.akka.management" %% "akka-management" % "1.1.4",
    "com.typesafe.akka" %% "akka-testkit" % AkkaVersion % Test,
    "com.typesafe.akka" %% "akka-http-testkit" % AkkaHttpVersion % Test,
    "com.typesafe.akka" %% "akka-distributed-data" % AkkaVersion % Test,
    "org.scalatest" %% "scalatest" % ScalaTestVersion % Test,
    "org.scalatestplus" %% "junit-4-13" % ScalaTestPlusJUnitVersion % Test
  )
}
