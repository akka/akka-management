addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.3.1")

addSbtPlugin("de.heikoseeberger" % "sbt-header" % "5.2.0")
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.0.0-RC5")
addSbtPlugin("com.dwijnand" % "sbt-dynver" % "3.3.0")
addSbtPlugin("com.lightbend.akka" % "sbt-paradox-akka" % "0.23")
addSbtPlugin("com.lightbend.paradox" % "sbt-paradox-project-info" % "1.1.3")
addSbtPlugin("com.eed3si9n" % "sbt-unidoc" % "0.4.2")
addSbtPlugin("com.typesafe.sbt" % "sbt-site" % "1.4.0")
addSbtPlugin("com.lightbend.sbt" % "sbt-publish-rsync" % "0.1")
addSbtPlugin("com.thoughtworks.sbt-api-mappings" % "sbt-api-mappings" % "3.0.0")
addSbtPlugin("org.foundweekends" % "sbt-bintray" % "0.5.4")
addSbtPlugin("com.lightbend" % "sbt-whitesource" % "0.1.14")
addSbtPlugin("com.typesafe.sbt" % "sbt-git" % "1.0.0")

resolvers += Resolver.jcenterRepo
