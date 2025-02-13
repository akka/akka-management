resolvers += "Akka library repository".at("https://repo.akka.io/maven")

addSbtPlugin("com.github.sbt" % "sbt-native-packager" % "1.11.0")

addSbtPlugin("de.heikoseeberger" % "sbt-header" % "5.10.0")
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.5.4")
addSbtPlugin("com.github.sbt" % "sbt-dynver" % "5.1.0")
addSbtPlugin("io.akka" % "sbt-paradox-akka" % "24.10.6")
addSbtPlugin("com.github.sbt" % "sbt-unidoc" % "0.5.0")
addSbtPlugin("com.github.sbt" % "sbt-site-paradox" % "1.7.0")
addSbtPlugin("com.lightbend.sbt" % "sbt-publish-rsync" % "0.3")
addSbtPlugin("com.thoughtworks.sbt-api-mappings" % "sbt-api-mappings" % "3.0.2")
addSbtPlugin("com.github.sbt" % "sbt-ci-release" % "1.9.2")
addSbtPlugin("com.github.sbt" % "sbt-git" % "2.0.1")
addSbtPlugin("com.typesafe" % "sbt-mima-plugin" % "1.1.4")

resolvers += Resolver.jcenterRepo
