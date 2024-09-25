enablePlugins(JavaAppPackaging)

resolvers += "Akka library repository".at("https://repo.akka.io/maven")

name := "bootstrap-demo-dns-api"

scalaVersion := "2.13.14"

def akkaManagementVersion(version: String) = version.split('+')(0)

libraryDependencies += "com.lightbend.akka.management" %% "akka-management-cluster-bootstrap" % akkaManagementVersion(
  version.value)

libraryDependencies += "com.lightbend.akka.management" %% "akka-management-cluster-http" % akkaManagementVersion(
  version.value)

libraryDependencies += "com.typesafe.akka" %% "akka-discovery" % "2.10.0-M1"
