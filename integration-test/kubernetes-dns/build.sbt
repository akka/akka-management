import com.typesafe.sbt.packager.docker._

enablePlugins(JavaServerAppPackaging)

ThisBuild / resolvers += "lightbend-akka".at("https://repo.akka.io/maven/github_actions")

version := "1.3.3.7" // we hard-code the version here, it could be anything really
dockerCommands :=
  dockerCommands.value.flatMap {
    case ExecCmd("ENTRYPOINT", args @ _*) => Seq(Cmd("ENTRYPOINT", args.mkString(" ")))
    case v                                => Seq(v)
  }

dockerExposedPorts := Seq(8080, 8558, 2552)
dockerBaseImage := "docker.io/library/eclipse-temurin:17.0.8.1_1-jre"

dockerCommands ++= Seq(
  Cmd("USER", "root"),
  Cmd("RUN", "chgrp -R 0 . && chmod -R g=u .")
)
