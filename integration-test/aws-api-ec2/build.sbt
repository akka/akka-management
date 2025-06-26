enablePlugins(JavaAppPackaging)

ThisBuild / resolvers += "lightbend-akka".at("https://dl.cloudsmith.io/basic/lightbend/akka/maven/")
ThisBuild / credentials += Credentials(Path.userHome / ".sbt" / ".credentials")

Universal / packageName := "app" // should produce app.zip

libraryDependencies += "com.amazonaws" % "aws-java-sdk-cloudformation" % "1.12.635" % Test

libraryDependencies += "com.amazonaws" % "aws-java-sdk-autoscaling" % "1.12.635" % Test

libraryDependencies += "com.fasterxml.jackson.core" % "jackson-databind" % "2.18.4" // aws SDK depends on insecure jackson

libraryDependencies += "org.scalatest" %% "scalatest" % Dependencies.ScalaTestVersion % Test
