<a id="bootstrap"></a>
# Akka Cluster Bootstrap

Akka Cluster Bootstrap supports safely forming a new cluster from discovered nodes or joining a node to an existing cluster. 
It is an alternative to configuring  static `seed-nodes`.

Akka Cluster Bootstrap not require any additional system like etcd/zookeeper/consul to be run along side the Akka cluster in order to discover the seed-nodes.

## Prerequisites

Bootstrap depends on:

 * @ref:[Akka Discovery](../discovery/index.md) to discover other members of the cluster
 * @ref:[Akka Management](../akka-management.md) to host HTTP endpoints used during the bootstrap process
 
It is recommended to understand these before trying to use bootstrap.

## Dependency

Add `akka-management-cluster-bootstrap` and one or more discovery mechanisms `akka-discovery` 
implementation you'd like to use for the discovery process. 

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
:  @@snip [CompileOnly.scala]($management$/cluster-bootstrap/src/test/scala/doc/akka/management/cluster/bootstrap/ClusterBootstrapCompileOnly.scala) { #start }

Java
:  @@snip [CompileOnly.java]($management$/cluster-bootstrap/src/test/java/jdoc/akka/management/cluster/bootstrap/ClusterBootstrapCompileOnly.java) { #start }
   
   
Ensure that `seed-nodes` is not present in configuration and that `start()` is called on every node.

The following configuration is required, more details for each and additional configuration can be found in [reference.conf](https://github.com/akka/akka-management/blob/master/cluster-bootstrap/src/main/resources/reference.conf):

* `akka.management.cluster.bootstrap.contact-point-discovery` set to the name to lookup in service discovery
* `akka.discovery.method` set to the intended service discovery mechanism


## How it works

* Service discovery is queried to get initial contact points. At least `contact-point-discovery.required-contact-point-nr` must be returned to start the bootstrap process.
* Each node exposes a HTTP endpoint `/bootstrap/seed-nodes`. This is exposed automatically by starting Akka management.
* Each node then probes the contact points's `/bootstrap/seed-ndes` endpoint to see if a cluster has already been formed
    * If it has it joins the cluster, bootstrap is finished
* If no cluster exists each node returns no `seed-nodes` and the node with the lowest address forms a new cluster and starts  advertising its self as a seed node
* Other nodes will see `/bootstrap/seed-nodes` of the node that self joined and join its cluster

See @ref[full bootsrap process and advanced configuration](details.md) for more details on the process. 

## Joining mechanism precedence

As Akka Cluster allows nodes to join to a cluster using a few methods it is the precedence of each method
is strictly defined and is as follows:

- If configured `akka.cluster.seed-nodes` (in your `application.conf`) are non-empty, those nodes will be joined, and bootstrap will NOT execute even if `start()` is called (a warning will be logged though).
- If an explicit `cluster.join` or `cluster.joinSeedNodes` is invokes before the bootstrap completes that
 joining would take precedence over the bootstrap. *This is however **not** recommended to mix multiple
 joining methods like this.*
- The Cluster Bootstrap mechanism takes some time to complete, but eventually issues a `joinSeednodes`.

@@@ warning
  It is NOT recommended to mix various joining mechanisms. Pick one mechanism and stick to it in order to
  avoid any surprises during cluster formation. E.g. do NOT set `akka.cluster.seed-nodes` if you are going
  to be using the Bootstrap mechanism. 
@@@



