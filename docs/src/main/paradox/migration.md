# Migration guide

## 1.0 

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

Unless used for something other than service discovery / bootstrap the following can be removed from your application.conf

```
- name: NAMESPACE	
   valueFrom:	
     fieldRef:	
       fieldPath: metadata.namespace
```

If `pod-namespace` is set remove from your configuration as it will be automatically picked up from the `/var/run/secrets/kubernetes.io/serviceaccount/namespace` file
that is mounted to each Kubernetes container. The namespace can be overridden with `pod-namespace` if this isn't the desired behavior.




