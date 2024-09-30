name := "native-image-tests"

version := "1.0"

scalaVersion := "2.13.15"

resolvers += "Akka library repository".at("https://repo.akka.io/maven")

lazy val akkaVersion = sys.props.getOrElse("akka.version", "2.10.0-M1")
lazy val akkaHttpVersion = sys.props.getOrElse("akka.http.version", "10.7.0-M1")

// Note: this default isn't really used anywhere so not important to bump
lazy val akkaManagementVersion = sys.props.getOrElse("akka.management.version", "1.5.2")

// Run in a separate JVM, to make sure sbt waits until all threads have
// finished before returning.
// If you want to keep the application running while executing other
// sbt tasks, consider https://github.com/spray/sbt-revolver/
fork := true

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion,
  "com.typesafe.akka" %% "akka-cluster-typed" % akkaVersion,
  "com.typesafe.akka" %% "akka-cluster" % akkaVersion,
  "com.typesafe.akka" %% "akka-discovery" % akkaVersion,
  "com.typesafe.akka" %% "akka-persistence" % akkaVersion,
  "com.typesafe.akka" %% "akka-cluster-sharding" % akkaVersion,
  "com.typesafe.akka" %% "akka-remote" % akkaVersion,
  "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
  "com.typesafe.akka" %% "akka-http-core" % akkaHttpVersion,
  "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion,
  "com.lightbend.akka.management" %% "akka-management" % akkaManagementVersion,
  "com.lightbend.akka.management" %% "akka-management-cluster-bootstrap" % akkaManagementVersion,
  "com.lightbend.akka.management" %% "akka-management-cluster-http" % akkaManagementVersion,
  "com.lightbend.akka.discovery" %% "akka-discovery-kubernetes-api" % akkaManagementVersion,
  "com.lightbend.akka.management" %% "akka-lease-kubernetes" % akkaManagementVersion,
  "com.lightbend.akka.management" %% "akka-rolling-update-kubernetes" % akkaManagementVersion,
  "ch.qos.logback" % "logback-classic" % "1.5.8"
)

// useful for investigations: sbt nativeImageRunAgent

// GraalVM native image build
enablePlugins(NativeImagePlugin)
nativeImageJvm := "graalvm-community"
nativeImageVersion := "21.0.2"
nativeImageOptions := Seq(
  "--no-fallback",
  "--verbose",
  "--initialize-at-build-time=ch.qos.logback",
  "-Dakka.native-image.debug=true"
)
