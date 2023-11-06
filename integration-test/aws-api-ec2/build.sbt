enablePlugins(JavaAppPackaging)

resolvers += "Akka library repository".at("https://repo.akka.io/maven")

Universal / packageName := "app" // should produce app.zip

libraryDependencies += "com.amazonaws" % "aws-java-sdk-cloudformation" % "1.12.561" % IntegrationTest

libraryDependencies += "com.amazonaws" % "aws-java-sdk-autoscaling" % "1.12.561" % IntegrationTest

libraryDependencies += "com.fasterxml.jackson.core" % "jackson-databind" % "2.15.3" // aws SDK depends on insecure jackson

libraryDependencies += "org.scalatest" %% "scalatest" % Dependencies.ScalaTestVersion % IntegrationTest
