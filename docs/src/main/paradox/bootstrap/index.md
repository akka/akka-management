<a id="bootstrap"></a>
# Akka Cluster Bootstrap

Akka Cluster Bootstrap helps forming (or joining to) a cluster by using @ref:[Akka Discovery](../discovery/index.md)
to discover peer nodes.  It is an alternative to configuring static `seed-nodes` in common dynamic deployment environments
such as on Kubernetes or AWS.

It builds on the flexibility of Akka Discovery, leveraging a range of discovery mechanisms depending on the
environment you want to run your cluster in.

## Prerequisites

Bootstrap depends on:

 * @ref:[Akka Discovery](../discovery/index.md) to discover other members of the cluster
 * @ref:[Akka Management](../akka-management.md) to host HTTP endpoints used during the bootstrap process

A discovery mechanism needs to be chosen. A good default choice is DNS.

## Dependency

Add `akka-management-cluster-bootstrap` and one or more discovery mechanisms to use for the discovery process.

For example, you might choose to use the DNS discovery and bootstrap extensions:

sbt
:   @@@vars
    ```scala
    libraryDependencies += "com.lightbend.akka.management" %% "akka-management-cluster-bootstrap" % "$version$"
    libraryDependencies += "com.lightbend.akka.discovery" %% "akka-discovery-dns"                % "$version$"
    ```
    @@@

Maven
:   @@@vars
    ```xml
    <dependency>
      <groupId>com.lightbend.akka.management</groupId>
      <artifactId>akka-management-cluster-bootstrap_$scala.binaryVersion$</artifactId>
      <version>$version$</version>
    </dependency>
    <dependency>
      <groupId>com.lightbend.akka.discovery</groupId>
      <artifactId>akka-discovery-dns_$scala.binaryVersion$</artifactId>
      <version>$version$</version>
    </dependency>
    ```
    @@@

Gradle
:   @@@vars
    ```gradle
    dependencies {
      compile group: "com.lightbend.akka.management", name: "akka-management-cluster-bootstrap_$scala.binaryVersion$", version: "$version$"
      compile group: "com.lightbend.akka.discovery", name: "akka-discovery-dns_$scala.binaryVersion$", version: "$version$"
    }
    ```
    @@@



## Using

Akka management must be started as well as the bootstrap process:

Scala
:  @@snip [CompileOnly.scala](/cluster-bootstrap/src/test/scala/doc/akka/management/cluster/bootstrap/ClusterBootstrapCompileOnly.scala) { #start }

Java
:  @@snip [CompileOnly.java](/cluster-bootstrap/src/test/java/jdoc/akka/management/cluster/bootstrap/ClusterBootstrapCompileOnly.java) { #start }


Ensure that `seed-nodes` is not present in configuration and that `start()` is called on every node.

The following configuration is required, more details for each and additional configuration can be found in [reference.conf](https://github.com/akka/akka-management/blob/master/cluster-bootstrap/src/main/resources/reference.conf):

* `akka.management.cluster.bootstrap.contact-point-discovery.service-name`: a unique name in the deployment environment for this cluster
  instance which is used to lookup peers in service discovery. If unset, it will be derived from the `ActorSystem` name.
* `akka.management.cluster.bootstrap.contact-point-discovery.discovery-method`: the intended service discovery mechanism (from what choices Akka Discovery provides).
  If unset, falls back to the system-wide default from `akka.discovery.method`.


## How It Works

* Each node exposes an HTTP endpoint `/bootstrap/seed-nodes`. This is provided by `akka-management-cluster-bootstrap` and
  exposed automatically by starting Akka management.
* During bootstrap each node queries service discovery repeatedly to get the initial contact points until at least the
  number of contact points as defined in `contact-point-discovery.required-contact-point-nr` has been found.
* Each node then probes the found contact points' `/bootstrap/seed-nodes` endpoint to see if a cluster has already
  been formed
    * If there is an existing cluster, it joins the cluster and bootstrapping is finished.
    * If no cluster exists, each node returns an empty list of `seed-nodes`. In that case, the node with the lowest
      address from the set of contact points forms a new cluster and starts advertising itself as a seed node.
* Other nodes will start to see the `/bootstrap/seed-nodes` of the node that self-joined and will join its cluster.

See @ref[full bootstrap process and advanced configuration](details.md) for more details on the process.

## Joining Mechanism Precedence

As Akka Cluster allows nodes to join to a cluster using a few methods, the precedence of each method
is strictly defined and is as follows:

- If `akka.cluster.seed-nodes` (in your `application.conf`) are non-empty, those nodes will be joined, and bootstrap
  will NOT execute even if `start()` is called, however a warning will be logged.
- If an explicit `cluster.join` or `cluster.joinSeedNodes` is invoked before the bootstrap completes, that
  joining would take precedence over the bootstrap (but it's not recommended to do so, see below).
- The Cluster Bootstrap mechanism takes some time to complete, but eventually issues a `joinSeednodes`.

@@@ warning
  It is NOT recommended to mix various joining mechanisms. Pick one mechanism and stick to it in order to
  avoid any surprises during cluster formation. E.g. do NOT set `akka.cluster.seed-nodes` if you are going
  to be using the Bootstrap mechanism.
@@@

## Bootstrap Recipes

To see how to configure and use bootstrap in various environments such as Kubernetes, see @ref[recipes](recipes.md).

@@@ index

* [details](details.md)
* [troubleshooting](troubleshooting.md)
* [local-config](local-config.md)
* [kuberntes-dns](kubernetes.md)
* [kuberntes-api](kubernetes-api.md)

@@@
