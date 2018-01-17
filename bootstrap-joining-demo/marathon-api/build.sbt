enablePlugins(JavaServerAppPackaging)
name := "bootstrap-joining-demo-marathon-api"

version := "0.1.0"

scalaVersion := "2.12.4"

libraryDependencies ++= Vector(
  "com.lightbend.akka.management" %% "akka-management-cluster-bootstrap" % "0.8.1",

  "com.lightbend.akka.management" %% "akka-management-cluster-http" % "0.8.1",

  "com.lightbend.akka.discovery" %% "akka-discovery-marathon-api" % "0.8.1"
)