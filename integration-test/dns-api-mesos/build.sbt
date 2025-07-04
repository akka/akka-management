enablePlugins(JavaAppPackaging)

ThisBuild / resolvers += "lightbend-akka".at("https://dl.cloudsmith.io/basic/lightbend/akka/maven/")
ThisBuild / credentials += Credentials(Path.userHome / ".sbt" / ".credentials")

name := "bootstrap-demo-dns-api"

scalaVersion := "2.13.15"

def akkaManagementVersion(version: String) = version.split('+')(0)

libraryDependencies += "com.lightbend.akka.management" %% "akka-management-cluster-bootstrap" % akkaManagementVersion(
  version.value)

libraryDependencies += "com.lightbend.akka.management" %% "akka-management-cluster-http" % akkaManagementVersion(
  version.value)

libraryDependencies += "com.typesafe.akka" %% "akka-discovery" % "2.10.0"
