enablePlugins(JavaServerAppPackaging)

name := "bootstrap-demo-marathon-api"

version := "0.1.0"

scalaVersion := "2.13.10"

val akkaManagementVersion = "1.4.2"

libraryDependencies ++= Vector(
  "com.lightbend.akka.management" %% "akka-management-cluster-bootstrap" % akkaManagementVersion,
  "com.lightbend.akka.management" %% "akka-management-cluster-http" % akkaManagementVersion,
  "com.lightbend.akka.discovery" %% "akka-discovery-marathon-api" % akkaManagementVersion
)
