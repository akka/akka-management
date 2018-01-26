enablePlugins(JavaAppPackaging)
name := "bootstrap-joining-demo-dns-api"

scalaVersion := "2.12.4"

def akkaManagementVersion(version: String) = version.split('+')(0)

libraryDependencies += "com.lightbend.akka.management" %% "akka-management-cluster-bootstrap" % akkaManagementVersion(version.value)

libraryDependencies += "com.lightbend.akka.management" %% "akka-management-cluster-http" % akkaManagementVersion(version.value)

libraryDependencies += "com.lightbend.akka.discovery" %% "akka-discovery-dns" % akkaManagementVersion(version.value)

