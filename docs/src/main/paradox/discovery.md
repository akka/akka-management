<a id="discovery"></a>
# Service Discovery

Akka Discovery provides a simple interface around various ways of locating services, such as DNS
or using configuration or key-value stores like zookeeper, consul etc.

## What is Service Discovery

Akka's Service Discovery talks specifically about discovering hosts and ports that relate to some
logical name of a service.

If you're looking for a way to discover Actors in a Cluster, you may want to look at the Receptionist
pattern from Akka Typed instead. Since it provides a more fine-tuned towards Actors mechanism of
doing the discovery.

# Implementations

TODO: Discussion about DNS vs other key-value stores.

## Akka DNS Discovery

The simplest, and also most natural form of service discovery is to use DNS as the source of truth regarding available 
services. In the simplest version, we query for a service name -- which each cluster manager, such as Kubernetes, Mesos 
or others define using their own naming schemes, and expect to get back a list of IPs that are related to this service.

### Dependencies and usage

Using `akka-discovery-dns` is very simple, as you simply need to depend on the library::

sbt
:   @@@vars
    ```scala
    libraryDependencies += "com.lightbend.akka" %% "akka-discovery-dns" % "$version$"
    ```
    @@@

Maven
:   @@@vars
    ```xml
    <dependency>
      <groupId>com.lightbend.akka</groupId>
      <artifactId>akka-discovery-dns_$scala.binaryVersion$</artifactId>
      <version>$version$</version>
    </dependency>
    ```
    @@@

Gradle
:   @@@vars
    ```gradle
    dependencies {
      compile group: "com.lightbend.akka", name: "akka-discovery-dns_$scala.binaryVersion$", version: "$version$"
    }
    ```
    @@@
    
And configure it to be used as default discovery implementation in your `application.conf`:

```
akka.discovery {
  impl = akka.discovery.akka-dns
}
```

From there on, you can use the generic API that hides the fact which discovery method is being used by calling::

Scala
```
import akka.discovery.ServiceDiscovery

val system = ActorSystem("Example")
// ... 
val discovery = ServiceDiscovery(system).discovery
```

Java
```
import akka.discovery.ServiceDiscovery;

ActorSystem system = ActorSystem.create("Example");
// ... 
SimpleServiceDiscovery discovery = ServiceDiscovery.get(system).discovery();
```

### Mechanism explanation

The simplest way of resolving multiple hosts of a (micro-)service is to perform a DNS lookup and treat all returned
`A` records as hosts of the same service cluster. This is how such lookup would look like in Kubernetes (see the 
`bootstrap-joining-demo` demo application if you want to try it out for yourself):

```
$ kubectl exec -it $POD -- dig appka-service.default.svc.cluster.local

; <<>> DiG 9.10.3-P4-Debian <<>> appka-service.default.svc.cluster.local
;; global options: +cmd
;; Got answer:
;; ->>HEADER<<- opcode: QUERY, status: NOERROR, id: 3457
;; flags: qr aa rd ra; QUERY: 1, ANSWER: 4, AUTHORITY: 0, ADDITIONAL: 0

;; QUESTION SECTION:
;appka-service.default.svc.cluster.local. IN A

;; ANSWER SECTION:
appka-service.default.svc.cluster.local. 30 IN A 172.17.0.6
appka-service.default.svc.cluster.local. 30 IN A 172.17.0.2
appka-service.default.svc.cluster.local. 30 IN A 172.17.0.3
appka-service.default.svc.cluster.local. 30 IN A 172.17.0.4

;; Query time: 0 msec
;; SERVER: 10.0.0.10#53(10.0.0.10)
;; WHEN: Fri Dec 08 12:04:38 UTC 2017
;; MSG SIZE  rcvd: 121
```

As you can see, this service consists of 4 nodes, with IPs `172.17.0.2` through `172.17.0.6`.
The "lowest" address (since in this case we assume they all listen on the same management port)

An improved way of DNS discovery are `SRV` records, which are not yet supported by `akka-discovery-dns`,
but would then allow the nodes to also advertise which port they are listening on instead of having to assume a shared 
known port (which in the case of the akka management routes is `19999`).

## How to contribute implementations

Contributions to alternative data-stores or service-discovery APIs built-in to specific cloud systems
are happily accepted. Please open an issue on the github issue tracker to discuss the integration
you'd like to contribute first.

An implementation should keep its configuration in the `akka.discovery`
