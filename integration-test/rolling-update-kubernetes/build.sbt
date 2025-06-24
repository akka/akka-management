enablePlugins(JavaAppPackaging, DockerPlugin)

ThisBuild / resolvers += "lightbend-akka".at("https://dl.cloudsmith.io/basic/lightbend/akka/maven/")
ThisBuild / credentials += Credentials(Path.userHome / ".sbt" / ".credentials")

version := "1.3.3.7" // we hard-code the version here, it could be anything really

dockerExposedPorts := Seq(8080, 8558, 2552)
dockerBaseImage := "docker.io/library/eclipse-temurin:17.0.8.1_1-jre"
dockerUpdateLatest := true
