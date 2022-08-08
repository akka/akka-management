enablePlugins(JavaAppPackaging)

Universal / packageName := "app" // should produce app.zip

libraryDependencies += "com.amazonaws" % "aws-java-sdk-cloudformation" % "1.12.276" % IntegrationTest

libraryDependencies += "com.amazonaws" % "aws-java-sdk-autoscaling" % "1.12.213" % IntegrationTest

libraryDependencies += "com.fasterxml.jackson.core" % "jackson-databind" % "2.9.7" // aws SDK depends on insecure jackson

libraryDependencies += "org.scalatest" %% "scalatest" % Dependencies.ScalaTestVersion % IntegrationTest
