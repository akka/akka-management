# Akka Management

Akka Management is the core module of the management utilities which provides a central HTTP endpoint for Akka
management extensions.

## Project Info

@@project-info{ projectId="akka-management" }

## Dependencies

Akka management requires Akka 2.5 or later.

The main Akka Management dependency is called `akka-management`. By itself however it does not provide any capabilities,
and you have to combine it with the management extension libraries that you want to make use of (e.g. cluster http management,
or cluster bootstrap). This design choice enables users to include only the minimal set of features they
actually want to use (and load) in their project.

@@dependency[sbt,Gradle,Maven] {
  group="com.lightbend.akka.management"
  artifact="akka-management_$scala.binary_version$"
  version="$project.version$"
}

And in addition to that, include all of the dependencies for the features you'd like to use,
like `akka-management-bootstrap` etc. Refer to each extensions documentation page to learn about how
to configure and use it.

## Basic Usage

Remember that Akka Management does not start automatically and the routes will only be exposed once you trigger:

Scala
:   @@snip[DemoApp.scala](/integration-test/kubernetes-api/src/main/scala/akka/cluster/bootstrap/DemoApp.scala){ #start-akka-management }

Java
:   @@snip[DemoApp.java](/integration-test/kubernetes-api-java/src/main/java/akka/cluster/bootstrap/demo/DemoApp.java){ #start-akka-management }
    
This allows users to prepare anything further before exposing routes for 
the bootstrap joining process and other purposes.


## Basic Configuration

You can configure hostname and port to use for the HTTP Cluster management by overriding the following:

@@snip[application.conf](/management/src/test/scala/akka/management/AkkaManagementHttpEndpointSpec.scala){ #management-host-port }

Note that the default value for hostname is `InetAddress.getLocalHost.getHostAddress`, which may or may not evaluate to
`127.0.0.1`.

When running Akka nodes behind NATs or inside docker containers in bridge mode,
it is necessary to set different hostname and port number to bind for the HTTP Server for Http Cluster Management:

application.conf
:   ```hocon
  # Get hostname from environmental variable HOST
  akka.management.http.hostname = ${HOST}
  # Use port 8558 by default, but use environment variable PORT_8558 if it is defined
  akka.management.http.port = 8558
  akka.management.http.port = ${?PORT_8558}
  # Bind to 0.0.0.0:8558 'internally': 
  akka.management.http.bind-hostname = 0.0.0.0
  akka.management.http.bind-port = 8558
    ```

It is also possible to modify the base path of the API, by setting the appropriate value in application.conf:

application.conf
:   ```hocon
    akka.management.http.base-path = "myClusterName"
    ```

In this example, with this configuration, then the Akka Management routes will will be exposed at under the `/myClusterName/...`,
base path. For example, when using Akka Cluster Management routes the members information would then be available under
`/myClusterName/shards/{name}` etc.

## Read only routes

By default extensions to Akka Management should only provide read only routes. This can be changed
via setting `akka.management.http.route-providers-read-only` to `false`. Each extension can access
the value of this property via `ManagementRouteProviderSettings.readOnly` to decide which routes to expose.

For example the `cluster-http` extension only provides read only access to Cluster membership but if `route-provider-read-only` is set
to `false` additional endpoints for managing the cluster are exposed e.g. downing members.

## Configuring Security

@@@ note

HTTPS is not enabled by default, as additional configuration from the developer is required. This module does not provide security by default.
It is the developer's choice to add security to this API, and when. If enabled, it is generally advisable not to expose management endpoints
publicly.

@@@

The non-secured usage of the module is as follows:

Scala
:   @@snip[DemoApp.scala](/integration-test/kubernetes-api/src/main/scala/akka/cluster/bootstrap/DemoApp.scala){ #start-akka-management }

Java
:   @@snip[DemoApp.java](/integration-test/kubernetes-api-java/src/main/java/akka/cluster/bootstrap/demo/DemoApp.java){ #start-akka-management }

### Enabling TLS/SSL (HTTPS) for Cluster HTTP Management

To enable SSL you need to provide an `SSLContext`. You can find more information about it in
@extref:[Server HTTPS Support](akka-http:server-side/server-https-support.html).

Scala
:   @@snip[AkkaManagementHttpEndpointSpec.scala](/management/src/test/scala/akka/management/AkkaManagementHttpEndpointSpec.scala){ #start-akka-management-with-https-context }

Java
:   @@snip[CodeExamples.java](/management/src/test/java/akka/management/CodeExamples.java){ #start-akka-management-with-https-context }

You can also refer to [AkkaManagementHttpEndpointSpec](https://github.com/akka/akka-management/blob/119ad1871c3907c2ca528720361b8ccb20234c55/management/src/test/scala/akka/management/AkkaManagementHttpEndpointSpec.scala#L124-L148) where a full example configuring the HTTPS context is shown.

### Enabling Basic Authentication

To enable Basic Authentication you need to provide an authenticator object before starting the management extension.
You can find more information in @extref:[Authenticate Basic Async directive](akka-http:http/routing-dsl/directives/security-directives/authenticateBasicAsync.html)

Scala
:  @@snip[CompileOnly.scala](/management/src/test/scala/akka/management/CompileOnly.scala){ #basic-auth }

Java
:  @@snip[CodeExamples.java](/management/src/test/java/akka/management/CodeExamples.java){ #basic-auth }


You can combine the two security options in order to enable HTTPS as well as basic authentication.
In order to do this, invoke `start(transformSettings)` where `transformSettings` is a function
to amend the `ManagementRouteProviderSettings`. Use `.withAuth` and `.withHttpsConnectionContext`
if the `ManagementRouteProviderSettings` to enable authentication and HTTPS respectively.

## Stopping Akka Management

In a dynamic environment you might stop instances of Akka Management, for example if you want to free up resources
taken by the HTTP server serving the Management routes.

You can do so by calling `stop()` on @scaladoc[AkkaManagement](akka.management.scaladsl.AkkaManagement).
This method return a `Future[Done]` to inform when the server has been stopped.

Scala
:  @@snip[CompileOnly.java](/management/src/test/scala/akka/management/CompileOnly.scala) { #stopping }

Java
:  @@snip[CodeExamples.java](/management/src/test/scala/akka/management/CompileOnly.scala) { #stopping }

## Developing Extensions

This project provides a set of management extensions. To write third-party extensions to Akka Management, here
are few pointers about how it all works together.

The `akka-management` module provides the central HTTP endpoint to which extensions can register themselves.

An extension can contribute to the exposed HTTP routes by defining named route providers in the
`akka.management.http.routes` configuration section in its own `reference.conf`. The core `AkkaManagement`
extension collects all the routes and serves them together under the Management HTTP server. This enables
easy extension of management capabilities (such as health-checks or cluster information etc)
without the boilerplate and overhead to start separate HTTP servers for each extension.

For example, the "Cluster HTTP Management" module exposes HTTP routes that can be used to monitor,
and even trigger joining/leaving/downing decisions via HTTP calls to these routes. The routes and
logic for these are implemented inside the `akka-management-cluster-http`.

Management route providers should be regular extensions that additionally extend the
`akka.management.scaladsl.ManagementRoutesProvider` or `akka.management.javadsl.ManagementRoutesProvider`
interface.

Libraries may register routes into the management routes by defining entries to this setting
the library `reference.conf`:

```
akka.management.http.routes {
  name = "FQCN"
}
```

Where the `name` of the entry should be unique to allow different route providers to be registered
by different libraries and applications.

The FQCN is the fully qualified class name of the `ManagementRoutesProvider`.

Route providers included by a library (from reference.conf) can be excluded by an application
by using `""` or `null` as the FQCN of the named entry, for example:

```
akka.management.http.routes {
  cluster-management = ""
}
```

As a best practice, Management extensions that do something proactively should not be
started automatically, but rather manually by the user. One example of that is Cluster Bootstrap.
It contributes routes to Akka Management, but the bootstrapping process does not start unless
`ClusterBootstrap().start()` is invoked. Thus, the user can decide when exactly
the application is ready to start joining an existing cluster.
