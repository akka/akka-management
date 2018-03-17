enablePlugins(JavaAppPackaging)
name := "bootstrap-joining-demo-aws-api-ec2"

version := "1.0"

scalaVersion := "2.12.4"

libraryDependencies += "com.lightbend.akka.management" % "akka-management-cluster-bootstrap_2.12" % "0.8.0"

libraryDependencies += "com.lightbend.akka.management" % "akka-management-cluster-http_2.12" % "0.8.0"

libraryDependencies += "com.lightbend.akka.discovery" % "akka-discovery-aws-api_2.12" % "0.8.0"

