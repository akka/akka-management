import com.typesafe.sbt.packager.docker._

enablePlugins(JavaServerAppPackaging)

version := "1.3.3.7" // we hard-code the version here, it could be anything really
dockerCommands :=
  dockerCommands.value.flatMap {
    case ExecCmd("ENTRYPOINT", args @ _*) => Seq(Cmd("ENTRYPOINT", args.mkString(" ")))
    case v => Seq(v)
  }


dockerExposedPorts := Seq(8080, 8558, 2552)
dockerBaseImage := "openjdk:8-jre-alpine"

dockerCommands ++= Seq(
  Cmd("USER", "root"),
  Cmd("RUN", "/sbin/apk", "add", "--no-cache", "bash", "bind-tools", "busybox-extras", "curl", "strace")
)
