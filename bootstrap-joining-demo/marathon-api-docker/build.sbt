enablePlugins(JavaServerAppPackaging)
name := "bootstrap-joining-demo-marathon-api-docker"

version := "1.0"

scalaVersion := "2.12.4"

//TODO: update to the latest release with Marathon API Docker support
val akkaVersion = "0.9.0-SNAPSHOT"

libraryDependencies ++= Vector(
  "com.lightbend.akka.management" %% "akka-management-cluster-bootstrap" % akkaVersion,

  "com.lightbend.akka.management" %% "akka-management-cluster-http" % akkaVersion,

  "com.lightbend.akka.discovery" %% "akka-discovery-marathon-api" % akkaVersion
)