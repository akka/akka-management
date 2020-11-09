## Consul

@@@ warning

This module is community maintained and the Lightbend subscription doesn't cover support for this module.
  It is also marked as @extref:[may change](akka:common/may-change.html).
  That means that the API, configuration or semantics can change without warning or deprecation period.

@@@

Consul currently ignores all fields apart from service name. This is expected to change.

If you are using Consul to do the service discovery this would allow you to base your Cluster on Consul services.

## Project Info

@@project-info{ projectId="akka-discovery-consul" }

@@dependency[sbt,Gradle,Maven] {
  symbol1=AkkaManagementVersion
  value1=$project.version$
  group="com.lightbend.akka.discovery"
  artifact="akka-discovery-consul_$scala.binary.version$"
  version=AkkaManagementVersion
}

`akka-discovery-consul` can be used with Akka $akka.version$ or $akka.version26$ or later.
You have to override the following Akka dependencies by defining them explicitly in your build and
define the Akka version to the one that you are using. Latest patch version of Akka is recommended and
a later version than $akka.version26$ can be used.

@@dependency[sbt,Gradle,Maven] {
  symbol=AkkaVersion
  value=$akka.version26$
  group=com.typesafe.akka
  artifact=akka-cluster_$scala.binary.version$
  version=AkkaVersion
  group2=com.typesafe.akka
  artifact2=akka-discovery_$scala.binary.version$
  version2=AkkaVersion
}

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


