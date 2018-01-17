enablePlugins(JavaAppPackaging)
name := "bootstrap-joining-demo-dns-api"

version := "1.0"

scalaVersion := "2.12.4"

val akkaManagementVersion = "0.8.0"

libraryDependencies += "com.lightbend.akka.management" %% "akka-management-cluster-bootstrap" % akkaManagementVersion

libraryDependencies += "com.lightbend.akka.management" %% "akka-management-cluster-http" % akkaManagementVersion

libraryDependencies += "com.lightbend.akka.discovery" %% "akka-discovery-dns" % akkaManagementVersion

