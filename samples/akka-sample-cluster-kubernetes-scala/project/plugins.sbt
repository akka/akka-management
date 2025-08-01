ThisBuild / resolvers += "lightbend-akka".at("https://repo.akka.io/maven/github_actions")

addSbtPlugin("com.github.sbt" % "sbt-native-packager" % "1.10.0")
addSbtPlugin("com.github.sbt" % "sbt-dynver" % "5.0.1")
