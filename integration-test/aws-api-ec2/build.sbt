enablePlugins(JavaAppPackaging)

resolvers += "Akka library repository".at("https://repo.akka.io/maven")

Universal / packageName := "app" // should produce app.zip

libraryDependencies += "com.amazonaws" % "aws-java-sdk-cloudformation" % "1.12.635" % Test

libraryDependencies += "com.amazonaws" % "aws-java-sdk-autoscaling" % "1.12.635" % Test

libraryDependencies += "com.fasterxml.jackson.core" % "jackson-databind" % "2.17.3" // aws SDK depends on insecure jackson

libraryDependencies += "org.scalatest" %% "scalatest" % Dependencies.ScalaTestVersion % Test
