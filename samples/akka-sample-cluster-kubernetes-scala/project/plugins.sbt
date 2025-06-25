ThisBuild / resolvers += "lightbend-akka".at("https://dl.cloudsmith.io/basic/lightbend/akka/maven/")
ThisBuild / credentials += Credentials(Path.userHome / ".sbt" / ".credentials")

addSbtPlugin("com.github.sbt" % "sbt-native-packager" % "1.11.1")
addSbtPlugin("com.github.sbt" % "sbt-dynver" % "5.1.0")
