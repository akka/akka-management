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
then collects all the routes and serves them together under the management HTTP server. This is in order
to avoid having to start an additional HTTP server for each additional extension, and also, it allows
easy extension of routes served by including libraries that offer new capabilities (such as health-checks or
cluster information etc).

Management extensions whose nature is "active" (as in, they "do something proactively") should not be
started automatically, but instead be started manually by the user. One example of that is the Cluster
Bootstrap, which on one hand does contributes routes to Akka Management, however the bootstraping process
does not start unless `ClusterBootstrap().start()` is invoked, which allows the user to decide when exactly
it is ready and wants to start joining an existing cluster.

![project structure](images/structure.png)

## Dependencies

Akka management requires Akka 2.5 or later.

The main Akka Management dependency is called `akka-management`. By itself however it does not provide any capabilities,
and you have to combine it with the management extension libraries that you want to make use of (e.g. cluster http management,
or cluster bootstrap). This design choice was made to make users able to include only the minimal set of features that they 
actually want to use (and load) in their project.

@@dependency[sbt,Gradle,Maven] {
  group="com.lightbend.akka.management"
  artifact="akka-management_$scala.binary_version$"
  version="$version$"
}

And in addition to that, include all of the dependencies for the features you'd like to use,
like `akka-management-bootstrap` etc. Refer to each extensions documentation page to learn about how
to configure and use it.

## Basic usage

Remember that Akka Management does not start automatically and the routes will only be exposed once you trigger:

Scala
:   @@snip[DemoApp.scala](/bootstrap-demo/kubernetes-api/src/main/scala/akka/cluster/bootstrap/DemoApp.scala){ #start-akka-management }

Java
:   @@snip[DemoApp.java](/bootstrap-demo/kubernetes-api-java/src/main/java/akka/cluster/bootstrap/demo/DemoApp.java){ #start-akka-management }
    
This is in order to allow users to prepare anything else they might want to prepare or start before exposing routes for 
the bootstrap joining process and other purposes.


## Basic Configuration

You can configure hostname and port to use for the HTTP Cluster management by overriding the following:

@@snip[application.conf](/management/src/test/scala/akka/management/http/AkkaManagementHttpEndpointSpec.scala){ #management-host-port }

Note that the default value for hostname is `InetAddress.getLocalHost.getHostAddress`, which may or may not evaluate to
`127.0.0.1`.

When running akka nodes behind NATs or inside docker containers in bridge mode, 
it is necessary to set different hostname and port number to bind for the HTTP Server for Http Cluster Management:

application.conf
:   ```hocon
  # Get hostname from environmental variable HOST
  akka.management.http.hostname = ${HOST} 
  # Use port 8558 by default, but use environment variable PORT_8558 if it is defined
  akka.management.port = 8558
  akka.management.port = ${?PORT_8558}
  # Bind to 0.0.0.0:8558 'internally': 
  akka.management.http.bind-hostname = 0.0.0.0
  akka.management.http.bind-port = 8558
    ```  

It is also possible to modify the base path of the API, by setting the appropriate value in application.conf: 

application.conf
:   ```hocon
    akka.management.http.base-path = "myClusterName"
    ```

In this example, with this configuration, then the akka management routes will will be exposed at under the `/myClusterName/...`,
base path. For example, when using Akka Cluster Management routes the members information would then be available under
`/myClusterName/shards/{name}` etc.


## Configuring Security

@@@ note

HTTPS is not enabled by default as additional configuration from the developer is required This module does not provide security by default. It's the developer's choice to add security to this API.

@@@

The non-secured usage of the module is as follows:

Scala
:   @@snip[DemoApp.scala](/bootstrap-demo/kubernetes-api/src/main/scala/akka/cluster/bootstrap/DemoApp.scala){ #start-akka-management }

Java
:   @@snip[DemoApp.java](/bootstrap-demo/kubernetes-api-java/src/main/java/akka/cluster/bootstrap/demo/DemoApp.java){ #start-akka-management }

### Enabling TLS/SSL (HTTPS) for Cluster HTTP Management

To enable SSL you need to provide an `SSLContext`. You can find more information about it in @extref[Server side https support](akka-http-docs:scala/http/server-side-https-support)

Scala
:   @@snip[AkkaManagementHttpEndpointSpec.scala](/management/src/test/scala/akka/management/http/AkkaManagementHttpEndpointSpec.scala){ #start-akka-management-with-https-context }

Java
:   @@snip[CodeExamples.java](/management/src/test/java/akka/management/http/CodeExamples.java){ #start-akka-management-with-https-context }
    
You can also refer to [AkkaManagementHttpEndpointSpec](https://github.com/akka/akka-management/blob/119ad1871c3907c2ca528720361b8ccb20234c55/management/src/test/scala/akka/management/http/AkkaManagementHttpEndpointSpec.scala#L124-L148) where a full example configuring the HTTPS context is shown.

### Enabling Basic Authentication

To enable Basic Authentication you need to provide an authenticator object before starting the management extension. 
You can find more information in @extref:[Authenticate Basic Async directive](akka-http-docs:scala/http/routing-dsl/directives/security-directives/authenticateBasicAsync)

Scala
:   @@snip[CompileOnly.scala](/management/src/test/scala/akka/management/http/CompileOnly.scala){ #basic-auth }

Java
:  @@snip[CodeExamples.java](/management/src/test/java/akka/management/http/CodeExamples.java){ #basic-auth }

@@@ note
  You can combine the two security options in order to enable HTTPS as well as basic authentication. 
  In order to do this, invoke both `setAsyncAuthenticator` as well as `setHttpsContext` *before* calling `start()`.
@@@

## Stopping Akka Management

In a dynamic environment you might stop instances of Akka Management, for example if you don't want to free up resources
taken by the HTTP server serving the Management routes. 

You can do so by calling `stop()` on @scaladoc[AkkaManagement](akka.management.http.AkkaManagement). 
This method return a `Future[Done]` to inform when the server has been stopped.

Scala
:   @@snip[CompileOnly.java](/management/src/test/scala/akka/management/http/CompileOnly.scala) { #stopping }

Java
:   @@snip[CodeExamples.java](/management/src/test/scala/akka/management/http/CompileOnly.scala) { #stopping }
