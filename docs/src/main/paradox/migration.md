# Migration guide

## 1.0 

Version requirements:

* Akka 2.5.19 or later
* Akka HTTP 10.1.7 or later

### Source changes

* `AkkaManagement` moved to package `akka.management.scaladsl.AkkaManagement`, if using from Java use `akka.management.javadsl.AkkaManagement`
* If implementing custom ManagementRouteProvider the package changed to `akka.management.scaladsl.ManagementRouteProvider`/`akka.management.javadsl.ManagementRouteProvider`
* `AkkaManagement.start` and `AkkaManagement.routes` may throw IllegalArgumentException instead of returning Try
* Auth and HTTPS has changed by using overloaded methods of `AkkaManagement.start` and `AkkaManagement.routes`, see the @ref[docs for more details](akka-management.md#enabling-basic-authentication)

### Configuration changes

* `akka.management.cluster.http.healthcheck.ready-states` moved to `akka.management.cluster.health-check.ready-states`
* `akka.management.cluster.bootstrap.form-new-cluster` renamed to `akka.management.cluster.bootstrap.new-cluster-enabled`

#### route-providers

`akka.management.cluster.route-providers` changed from being a list of fully qualified class names to
a configuration object `akka.management.cluster.routes` with named route providers. The reason for the
change was to be able to exclude a route provider that was included by a library (from reference.conf) by
using `""` or `null` as the FQCN of the named entry, for example:

```
akka.management.http.routes {
  health-checks = ""
}
```

By default the `akka.management.HealthCheckRoutes` is enabled.

### Akka Discovery

For Akka Management version 1.0 Service Discovery as well as the config, DNS and aggregate discovery methods 
were made core Akka module. The following steps are required when upgrading to 1.0 of Akka Management.

Remove dependencies for:

@@dependency[sbt,Gradle,Maven] {
  group="com.lightbend.akka.discovery"
  artifact="akka-discovery"
  version="old_akka_management_version"
  group2="com.lightbend.akka.discovery"
  artifact2="akka-discovery-dns"
  version2="old_akka_management_version"
  group3="com.lightbend.akka.discovery"
  artifact3="akka-discovery-config"
  version3="old_akka_management_version"
  group4="com.lightbend.akka.discovery"
  artifact4="akka-discovery-aggregate"
  version4="old_akka_management_version"
}

If using Cluster Bootstrap the new dependency will be brought in automatically.
If using Service Discovery directly add the following dependency:

@@dependency[sbt,Gradle,Maven] {
  group="com.typesafe.akka"
  artifact="akka-discovery"
  version="latest_akka_version"
}

Setting the service discovery method now has to be the unqualified name e.g `kubernetes-api` rather than `akka.discovery.kubernets-api`.
If using a custom discovery method the configuration for the discovery method must live under `akka.discovery`. 

For bootstrap it is recommended to set the service discovery method via `akka.management.cluster.bootstrap.contact-point-discovery.discovery-method`
rather then overriding the global service discovery mechanism with `akka.discovery.method` 

### DNS 

If using DNS service discovery it is no longer required to override the global Akka DNS resolver. Remove `akka.io.dns.resolver = async-dns` from your configuration
to avoid setting the `async-dns` as the global DNS resolver as it still lacks some features. The DNS discovery mechanism now uses an isolated resolver internally
to support SRV records. 

### Kubernetes

Kubernetes service discovery now automatically picks up the namespace at runtime. If previously hard coded or an environment variable used this can be removed
from configuration and the deployment.

Unless used for something other than service discovery / bootstrap the following can be removed from your deployment 

```
- name: NAMESPACE	
   valueFrom:	
     fieldRef:	
       fieldPath: metadata.namespace
```

If `pod-namespace` is set remove from your configuration as it will be automatically picked up from the `/var/run/secrets/kubernetes.io/serviceaccount/namespace` file
that is mounted to each Kubernetes container. The namespace can be overridden with `pod-namespace` if this isn't the desired behavior.

### Cluster HTTP

The `cluster-http` module now only exposes read only routes by default. To enable destructive operations such as downing members
set `akka.management.http.route-providers-read-only` to `false.



