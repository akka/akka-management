enablePlugins(JavaAppPackaging)

packageName in Universal := "app" // should produce app.zip

val akkaManagementVersion = SettingKey[String]("akkaManagementVersion", "Akka Management Version")

akkaManagementVersion := "latest.release" 

name := "bootstrap-joining-demo-aws-api"

scalaVersion := "2.12.4"

libraryDependencies += "com.lightbend.akka.management" % "akka-management-cluster-bootstrap_2.12" % akkaManagementVersion.value

libraryDependencies += "com.lightbend.akka.management" % "akka-management-cluster-http_2.12" % akkaManagementVersion.value

libraryDependencies += "com.lightbend.akka.discovery" % "akka-discovery-aws-api_2.12" % akkaManagementVersion.value

libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.4" % "test"

libraryDependencies += "com.amazonaws" % "aws-java-sdk-cloudformation" % "1.11.271" % "test"
