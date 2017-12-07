<a id="akka-management"></a>
# Akka Management

Akka Management is the central module of all the management utilities that pulls them all together.


The operations exposed are comparable to the Command Line Management tool or the JMX interface `akka-cluster` provides.

## Akka management architecture overview 

Akka management serves as the central piece that pulls together various Akka management extensions
and actually exposes them as simple HTTP endpoints. 

For example, the "Cluster HTTP Management" module exposes HTTP routes that can be used to monitor,
and even trigger joining/leaving/downing decisions via HTTP calls to these routes. The routes and
logic for these are implemented inside the `akka-management-cluster-http`, however that module serves
as a "plugin" to `akka-management` itself. 

Libraries (referred to as "management extensions") can contribute to the exposed HTTP routes by 
appending to the `akka.management.http.route-providers` list. The core `AkkaManagement` extension
then collects all the routes and serves them together under the management HTTP endpoint. This is in order
to avoid having to start multiple HTTP servers for each additional extension, and also, it allows
easy extension of routes served by including libraries that offer new capabilities (such as health-checks or
cluster information etc).

Management extensions those nature is "active" (as in, they "do something proactively") are not to be
started automatically, and should be still invoked by the user. One example of that is the Cluster
Bootstrap, which on one hand does contributes routes to Akka Management, however the bootstraping process
does not start unless `ClusterBootstrap().start()` is invoked, which allows the user to decide when exactly
it is ready and wants to start joining an existing cluster.

## Dependencies

Akka Management is a separate jar file. Make sure that you have the following dependency in your project::

sbt
:   @@@vars
    ```scala
    libraryDependencies += "com.lightbend.akka" %% "akka-management" % "$version$"
    ```
    @@@

Maven
:   @@@vars
    ```xml
    <dependency>
      <groupId>com.lightbend.akka</groupId>
      <artifactId>akka-management_$scala.binaryVersion$</artifactId>
      <version>$version$</version>
    </dependency>
    ```
    @@@

Gradle
:   @@@vars
    ```gradle
    dependencies {
      compile group: "com.lightbend.akka", name: "akka-management_$scala.binaryVersion$", version: "$version$"
    }
    ```
    @@@

And in addition to that, include all of the dependencies for the features you'd like to use,
like `akka-management-bootstrap` etc. Refer to each extensions documentation page to learn about how
to configure and use it.

# Configuring the management endpoint

The management endpoint has a number of settings which can be set before invoking 
`AkkaManagement(system).start()`.


## Configuration

Note that 

You can configure hostname and port to use for the HTTP Cluster management by overriding the following:

    akka.management.http.hostname = "127.0.0.1"
    akka.management.http.port = 19999

However, those are the values by default. In case you are running multiple cluster instances within the same JVM these
configuration parameters should allow you to expose different cluster management APIs by modifying the port:

Scala
:   ```
    //Config Actor system 1
    akka.management.http.hostname = "127.0.0.1"
    akka.management.http.port = 19999
    ...
    //Config Actor system 2
    akka.management.http.hostname = "127.0.0.1"
    akka.management.http.port = 20000
    ...
    val actorSystem1 = ActorSystem("as1", config1)
    val actorSystem2 = ActorSystem("as2", config2)
    ...
    AkkaManagement(actorSystem1).start()
    AkkaManagement(actorSystem2).start()
    ```

Java
:   ```java
    //Config Actor system 1
    akka.management.http.hostname = "127.0.0.1"
    akka.management.http.port = 19999
    ...
    //Config Actor system 2
    akka.management.http.hostname = "127.0.0.1"
    akka.management.http.port = 20000
    ...
    ActorSystem actorSystem1 = ActorSystem.create("as1", config1);
    ActorSystem actorSystem2 = ActorSystem.create("as2", config2);
    ...
    AkkaManagement.get(actorSystem1).start();
    AkkaManagement.get(actorSystem2).start();
    ```

It is also possible to modify the root path of the API (no root path by default). Provide your desired path when starting:

Scala
:   ```
    ClusterHttpManagement(cluster, "myClusterName").start()
    ```

Java
:   ```
    ClusterHttpManagement.create(cluster, "myClusterName").start();
    ```

In this example, with this configuration, then the cluster management apis will be exposed at `/myClusterName/members`, `/myClusterName/shards/{name}`, etc.


## Security

@@@ note

This module does not provide security by default. It's the developer's choice to add security to this API.

@@@

The non-secured usage of the module is as follows:

Scala
:   ```
    ClusterHttpManagement(cluster).start()
    ```

Java
:   ```
    ClusterHttpManagement.create(cluster).start();
    ```

### Enabling SSL for Cluster HTTP Management

To enable SSL you need to provide an `SSLContext`. You can find more information about it in @extref[Server side https support](akka-http-docs:scala/http/server-side-https-support)

Scala
:   ```
    val https: HttpsConnectionContext = ConnectionContext.https(sslContext)
    ClusterHttpManagement(cluster, https).start()
    ```

Java
:   ```
    HttpsConnectionContext https = ConnectionContext.https(sslContext);
    ClusterHttpManagement.create(cluster, https).start();
    ```

### Enabling Basic Authentication for Cluster HTTP Management

To enable Basic Authentication you need to provide an authenticator. You can find more information in @extref:[Authenticate Basic Async directive](akka-http-docs:scala/http/routing-dsl/directives/security-directives/authenticateBasicAsync)

Scala
:   ```
    def myUserPassAuthenticator(credentials: Credentials): Future[Option[String]] =
      credentials match {
        case p @ Credentials.Provided(id) =>
          Future {
            // potentially
            if (p.verify("p4ssw0rd")) Some(id)
            else None
          }
        case _ => Future.successful(None)
      }
    ...
    ClusterHttpManagement(cluster, myUserPassAuthenticator(_)).start()  
    ```

Java
:   ```
    ClusterHttpManagement.create(cluster, myUserPassAuthenticator).start();
    ```

### Enabling SSL and Basic Authentication for Cluster HTTP Management

To enable SSL and Basic Authentication you need to provide both an `SSLContext` and an authenticator.

Scala
:   ```
    def myUserPassAuthenticator(credentials: Credentials): Future[Option[String]] =
      credentials match {
        case p @ Credentials.Provided(id) =>
          Future {
            // potentially
            if (p.verify("p4ssw0rd")) Some(id)
            else None
          }
        case _ => Future.successful(None)
      }
    ...
    val https: HttpsConnectionContext = ConnectionContext.https(sslContext)
    ClusterHttpManagement(cluster, myUserPassAuthenticator(_), https).start()
    ```

Java
:   ```
    HttpsConnectionContext https = ConnectionContext.https(sslContext);
    ClusterHttpManagement.create(cluster, myUserPassAuthenticator, https).start();
    ```

## Stopping Cluster HTTP Management

In a dynamic environment you might want to start and stop multiple instances of HTTP Cluster Management.
You can do so by calling `stop()` on @scaladoc[ClusterHttpManagement](akka.management.http.ClusterHttpManagement). This method return a `Future[Done]` to inform when the
module has been stopped.

Scala
:   ```
    val httpClusterManagement = ClusterHttpManagement(cluster)
    httpClusterManagement.start()
    //...
    val bindingFuture = httpClusterManagement.stop()
    bindingFuture.onComplete { _ => println("It's stopped") }
    ```

Java
:   ```
    ClusterHttpManagement httpClusterManagement = ClusterHttpManagement.create(cluster);
    httpClusterManagement.start();
    //...
    httpClusterManagement.stop();
    ```

