## Consul

Consul currently ignores all fields apart from service name. This is expected to change.

If you are using Consul to do the service discovery this would allow you to base your Cluster on Consul services.

@@dependency[sbt,Gradle,Maven] {
  group="com.lightbend.akka.discovery"
  artifact="akka-discovery-consul_2.12"
  version="$version$"
}

@@@ note

This discovery implementation has been recently contributed and we do not fully "support" it yet.
However it should work fine and has been used in production by the person contributing it.

@@@

In your application conf add:
```
akka.discovery {
  method = akka-consul
  akka-consul {

    #How to connect to Consul to fetch services data
    consul-host = "127.0.0.1"
    consul-port = 8500

    # Prefix for consul tag with the name of the actor system / application name,
    # services with this tag present will be found by the discovery mechanism
    # i.e. `system:test` will be found in cluster if the cluster system is named `test`
    application-name-tag-prefix = "system:"

    # Prefix for tag containing port number where akka management is set up so that
    # the seed nodes can be found, an example value for the tag would be `akka-management-port:19999`
    application-akka-management-port-tag-prefix = "akka-management-port:"
  }
}
```

Notes:

* Since tags in Consul services are simple strings, prefixes are necessary to ensure that proper values are read.

* If Akka management port tag is not found on service in Consul the implementation defaults to catalog service port.


