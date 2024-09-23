# Building using SBT

sbt uses a plugin called [sbt-native-packager](https://www.scala-sbt.org/sbt-native-packager/) to allow conveniently packaging 
Java and Scala applications built using sbt as Docker images.


## Setup

To use this plugin in your sbt application, add the following to your `project/plugins.sbt` file:

@@@vars
```scala
addSbtPlugin("com.github.sbt" % "sbt-native-packager" % "<latest.version>")
```
@@@

Now you can enable the appropriate plugins in your build, by modifying your project in `build.sbt`:

```scala
enablePlugins(JavaAppPackaging, DockerPlugin)
```

Here we're telling native packager to package our application as a Java application that can be run from the command line. This will package up all the applications dependencies (jar files), and generate a start script to start the application. To generate this start script, native packager needs to know what the applications main class is. When the application only has one main class in its source folder, sbt will detect this automatically, but in case there are multiple, or the main class comes from a dependency, it can be set in `build.sbt` like so:

```scala
Compile / mainClass := Some("akka.sample.cluster.kubernetes.DemoApp")
```

### Selecting a JDK

We recommend using the Eclipse Temurin 17 base image:

```scala
dockerBaseImage := "docker.io/library/eclipse-temurin:17-jre"
```

For a full list of Lightbend certified JDK builds and versions, see [here](https://doc.akka.io/libraries/akka-dependencies/current/java-versions.html).

### Git hash based version numbers

This step is optional, but we recommend basing the version number of your application on the current git hash, since this ensures that you will always be able to map what is deployed to production back to the exact version of your application being used.

There are a number of sbt plugins available for generating a version number from a git hash, we're going to use [`sbt-dynver`](https://github.com/dwijnand/sbt-dynver), which incorporates the most recent git tag as the base version number, appends the git hash to that only if there are any changes since that tag, and also includes a datetime stamp if there are local changes in the repository. To add this plugin to your project, add the following to `project/plugins.sbt`:

@@@vars
```scala
addSbtPlugin("com.dwijnand" % "sbt-dynver" % "$sbt.dynver.version$")
```
@@@

For the plugin to work, you need to ensure that you *don't* specify a `version` in your sbt build, since this will overwrite the version that `sbt-dynver` generates.
`sbt-dynver` generates versions with a `+` character in them (the `+` is used to indicate how many commits have been added since the last tag, so `1.0+4` indicates this is the 1.0 tag plus 4 commits) and
this is invalid for docker versions. 
To replace this with a `-` character, add the following to `build.sbt`:

```scala
ThisBuild / dynverSeparator := "-"
```

You may also want to configure the sbt native packager to tag your image as the `latest` image, this will be necessary if you're using the `latest` tag in your deployment spec. To do this, enable `dockerUpdateLatest` in `build.sbt`:

```scala
dockerUpdateLatest := true
```

### Configuring deployment

After building the docker image we need to deploy it. The Docker username and repository can be hardcoded in your `build.sbt` or taken from a property such as: 

```scala
dockerUsername := sys.props.get("docker.username")
dockerRepository := sys.props.get("docker.registry")
```

In this case, we're reading both variables from system properties, which ensures that the build is not tied to any particular docker username or registry. We'll supply these system properties when we invoke sbt.
The repository can be [DockerHub](https://hub.docker.com/) or your private repository.

## Building the docker image

Now that we're setup, we can build our docker image. Run the following:

```
sbt -Ddocker.username=<user-name> -Ddocker.registry=<registry-url> docker:publish
```

