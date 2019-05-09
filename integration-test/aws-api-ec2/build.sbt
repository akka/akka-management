enablePlugins(JavaAppPackaging)

packageName in Universal := "app" // should produce app.zip

libraryDependencies += "com.amazonaws" % "aws-java-sdk-cloudformation" % "1.11.271" % IntegrationTest

libraryDependencies += "com.amazonaws" % "aws-java-sdk-autoscaling" % "1.11.271" % IntegrationTest

libraryDependencies += "com.fasterxml.jackson.core" % "jackson-databind" % "2.9.7" // aws SDK depends on insecure jackson

libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.8-RC4" % IntegrationTest


