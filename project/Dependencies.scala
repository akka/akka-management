import sbt._, Keys._

object Dependencies {

  val AkkaVersion = "2.4.20"
  val AkkaHttpVersion = "10.0.10"
  val JUnitVersion = "4.12"
  val SprayJsonVersion = "1.3.3"
  val akkaDns = "2.4.2"

  val Common = Seq(
    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest" % "3.0.1" % Test // ApacheV2
    )
  )

  val ClusterHttp = Seq(
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-cluster"                       % AkkaVersion,
      "com.typesafe.akka" %% "akka-cluster-sharding"              % AkkaVersion,
      "com.typesafe.akka" %% "akka-http"                          % AkkaHttpVersion,
      "com.typesafe.akka" %% "akka-http-spray-json"               % AkkaHttpVersion,
      "io.spray"          %% "spray-json"                         % SprayJsonVersion, // ApacheV2
      "ru.smslv.akka"     %% "akka-dns"                           % akkaDns,

      "com.typesafe.akka" %% "akka-distributed-data-experimental" % AkkaVersion     % "test",
      "com.typesafe.akka" %% "akka-http-testkit"                  % AkkaHttpVersion % "test",
      "junit"             % "junit"                               % JUnitVersion    % "test",
      "org.mockito"       % "mockito-all"                         % "1.10.19"       % "test"  // Common Public License 1.0
    )
  )

}
