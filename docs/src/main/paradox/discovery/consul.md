## Consul

@@@ warning

This module is community maintained and the Lightbend subscription doesn't cover support for this module.
  It is also marked as [may change](https://doc.akka.io/docs/akka/current/common/may-change.html).
  That means that the API, configuration or semantics can change without warning or deprecation period.

@@@

Consul currently ignores all fields apart from service name. This is expected to change.

If you are using Consul to do the service discovery this would allow you to base your Cluster on Consul services.

@@dependency[sbt,Gradle,Maven] {
  group="com.lightbend.akka.discovery"
  artifact="akka-discovery-consul_2.12"
  version="$version$"
}

In your application conf add:
```
akka.discovery {

  # Set the following in your application.conf if you want to use this discovery mechanism:
  # impl = akka-consul
  # ApiMayChange
  akka-consul {
    class = akka.discovery.consul.ConsulServiceDiscovery

    consul-host = "127.0.0.1"
    consul-port = 8500

    # the suffix to discover management service
    # your code should have logic to register SERVICE_NAME-akka-mangement to consul
    management-service-suffix = "-akka-management"
  }
}
```

In your code start bootstrap something like this:

```scala
// Akka Management hosts the HTTP routes used by bootstrap
val akkaManagement: AkkaManagement = AkkaManagement(system)

akkaManagement
  .start()
  .map { _ =>
    val consulAgent =
      Consul.builder().withHostAndPort(HostAndPort.fromParts("127.0.0.1", 8500)).build()
      val client = consulAgent.agentClient()
      client.register(
        /* port = */ akkaManagement.settings.getHttpPort,
        /* tcp = */ HostAndPort.fromParts(client.getAgent.getMember.getAddress, akkaManagement.settings.getHttpPort),
        /* interval = */ 10,
        /* name = */ "YOUR_SERVICE-akka-management",
        /* id = */ "YOUR_SERVICE-akka-management-8558",
        /* tags = */ Seq.empty[String].asJava,
        /* meta = */ Map.empty[String, String].asJava
      )
  }
  .recover {
    case err =>
      system.log.error(err, "Failed to start akka management")
  }

// Starting the bootstrap process needs to be done explicitly
ClusterBootstrap(system).start()
```

Note:

The binding of management service is set in `akka.management.http.hostname`, it should be exactly the same as

- the [`ServiceAddress`](https://www.consul.io/api/catalog.html#serviceaddress) (usually a hostname), if it's not empty,
- or [`Address`](https://www.consul.io/api/catalog.html#address-1) (usually an IP address), if the `ServiceAddress` is empty.

You may consider setting this config dynamically when the app starts, by:

```conf
akka.management.cluster.bootstrap.contact-point-discovery.service-name = YOUR_SERVICE
akka.management.http.hostname = ${app.privateIp}
```

And set `props` before actor system starts:

```scala
scala.sys.props += "app.privateIp" -> consulAgentClient.getAgent.getMember.getAddress
```
