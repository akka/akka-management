import com.typesafe.sbt.packager.docker._

enablePlugins(JavaServerAppPackaging)

version := "1.0"

dockerUsername := sys.env.get("DOCKER_USER")
