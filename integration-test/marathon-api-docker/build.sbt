import com.typesafe.sbt.packager.docker._

name := "bootstrap-demo-marathon-api-docker"

scalaVersion := "2.13.14"

enablePlugins(JavaServerAppPackaging)

resolvers += "Akka library repository".at("https://repo.akka.io/maven")

version := "1.0"

dockerUsername := sys.env.get("DOCKER_USER")

val akkaManagementVersion = "1.5.2"

libraryDependencies ++= Vector(
  "com.lightbend.akka.management" %% "akka-management-cluster-bootstrap" % akkaManagementVersion,
  "com.lightbend.akka.management" %% "akka-management-cluster-http" % akkaManagementVersion,
  "com.lightbend.akka.discovery" %% "akka-discovery-marathon-api" % akkaManagementVersion
)
