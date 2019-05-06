import com.typesafe.sbt.packager.docker._

enablePlugins(JavaServerAppPackaging)

//import com.typesafe.sbt.packager.graalvmnativeimage._
//enablePlugins(GraalVMNativeImagePlugin)
//
//graalVMNativeImageOptions ++= Seq(
//  "-H:ConfigurationFileDirectories=/home/aengelen/dev/akka-management/graal_config_generated/",
//  // Make sure we can create a native image even when excluding netty and aeron
//  "--allow-incomplete-classpath",
//  // SSL features must be included like this and *not* pulled in via reflect-config.json
//  "--allow-incomplete-classpath",
//  // Make sure we don't accidentally test a non-standalone image
//  "--no-fallback"
//)
////graalVMNativeImageOptions += "--delay-class-initialization-to-runtime=org.agrona.concurrent.AbstractConcurrentArrayQueue"

unmanagedJars in Compile += file(sys.env("GRAAL_HOME") + "/jre/lib/svm/builder/svm.jar")

assemblyExcludedJars in assembly := {
  val cp = (fullClasspath in assembly).value
  cp filter { f =>
    f.data.getName.contains("aeron") || f.data.getName.contains("netty")
  }
}

fork := true

javaOptions += "-agentlib:native-image-agent=trace-output=/tmp/trace-file.json"

version := "1.3.3.7" // we hard-code the version here, it could be anything really

dockerCommands :=
  dockerCommands.value.flatMap {
    case ExecCmd("ENTRYPOINT", args @ _*) => Seq(Cmd("ENTRYPOINT", args.mkString(" ")))
    case v => Seq(v)
  }

dockerExposedPorts := Seq(8080, 8558, 2552)
dockerBaseImage := "ubuntu-substratevm:latest"

dockerCommands ++= Seq(
  Cmd("USER", "root"),
  Cmd("RUN", "/sbin/apk", "add", "--no-cache", "bash", "bind-tools", "busybox-extras", "curl", "strace"),
  Cmd("RUN", "chgrp -R 0 . && chmod -R g=u .")
)

