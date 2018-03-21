scalaVersion := "2.12.4"
name := "bootstrap-joining-demo-aws-api-ecs"
version := "1.0"

libraryDependencies ++= Seq(
  "com.lightbend.akka.management" %% "akka-management-cluster-http" % "0.10.0",
  "com.lightbend.akka.management" %% "akka-management-cluster-bootstrap" % "0.10.0"
)

// TODO: To be removed when 0.11.0 is released (see note on AsyncEcsSimpleServiceDiscovery).
libraryDependencies ++= Seq(
  "software.amazon.awssdk" % "ecs" % "2.0.0-preview-9",
  "org.scala-lang.modules" %% "scala-java8-compat" % "0.8.0"
)

enablePlugins(JavaAppPackaging, AshScriptPlugin, DockerPlugin)

dockerBaseImage := "openjdk:8-jre-alpine"
