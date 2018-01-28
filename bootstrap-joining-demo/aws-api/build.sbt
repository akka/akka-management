enablePlugins(JavaAppPackaging)
name := "bootstrap-joining-demo-aws-api"

version := "1.0"

scalaVersion := "2.12.4"

libraryDependencies += "com.lightbend.akka.management" % "akka-management-cluster-bootstrap_2.12" % "0.8.0"

libraryDependencies += "com.lightbend.akka.management" % "akka-management-cluster-http_2.12" % "0.8.0"

libraryDependencies += "com.lightbend.akka.discovery" % "akka-discovery-aws-api_2.12" % "0.8.0"

libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.4" % "test"

libraryDependencies += "com.amazonaws" % "aws-java-sdk-cloudformation" % "1.11.271" % "test"


