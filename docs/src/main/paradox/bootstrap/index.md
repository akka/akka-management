<a id="bootstrap"></a>
# Akka Cluster Bootstrap

@@@ index

  - [Recipes](recipes.md)
  
@@@


Akka Cluster Bootstrap helps forming (or joining to) a cluster by using @ref:[Akka Discovery](../discovery/index.md)
to discover peer nodes.  It is an alternative to configuring static `seed-nodes` in dynamic deployment environments
such as on Kubernetes or AWS.

It builds on the flexibility of Akka Discovery, leveraging a range of discovery mechanisms depending on the
environment you want to run your cluster in.

## Prerequisites

Bootstrap depends on:

 * @ref:[Akka Discovery](../discovery/index.md) to discover other members of the cluster
 * @ref:[Akka Management](../akka-management.md) to host HTTP endpoints used during the bootstrap process

A discovery mechanism needs to be chosen. A good default choice is DNS.

## Project Info

@@project-info{ projectId="cluster-bootstrap" }

## Dependency

Add `akka-management-cluster-bootstrap` and one or more discovery mechanisms to use for the discovery process.

For example, you might choose to use the @extref:[DNS discovery](akka:discovery/index.html#discovery-method-dns)
and bootstrap extensions:

@@dependency[sbt,Gradle,Maven] {
  group=com.lightbend.akka.management
  artifact=akka-management-cluster-bootstrap_$scala.binary_version$
  version=$project.version$
  group2=com.typesafe.akka
  artifact2=akka-discovery_$scala.binary_version$
  version2=$akka.version$
}

@@@ note

`akka-discovery` is already a transitive dependency of `akka-management-cluster-bootstrap` but it can
be good to define it explicitly in the build of the application to align the Akka versions with other
dependencies from the application. The version must be the same across all Akka modules, e.g.
`akka-actor`, `akka-discovery` and `akka-cluster` must be of the same version.

The minimum supported Akka version is $akka.version$. Use the same Akka version for `akka-discovery`
as for other Akka dependencies in the application. Latest patch version of Akka is recommended and
a later version than $akka.version$ can be used.

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

As Akka Cluster allows nodes to join to a cluster using multiple different methods, the precedence of each method
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

## Deployment considerations

### Initial deployment

Cluster Bootstrap will always attempt to join an existing cluster if possible. However if no other contact point advertises any `seed-nodes` a
new cluster will be formed by the node decided by the `JoinDecider` where the default sorts the addresses then picks the lowest.

A setting is provided, `akka.management.cluster.bootstrap.new-cluster-enabled` that can be disable new cluster formation to only allow the
node to join existing clusters. 

* On initial deployment use the default `akka.management.cluster.bootstrap.new-cluster-enabled=on`
* Following the initial deployment it is recommended to set `akka.management.cluster.bootstrap.new-cluster-enabled=off` 
  with an immediate re-deployment once the initial cluster has formed
  
This can be used to provide additional safety during restarts and redeploys while
there is a network partition present. Without new cluster formation disabled an isolated set of nodes could form a new
cluster if all are restarted. 

For complete safety of the Initial Bootstrap it is recommended to set the `contact-point-discovery.required-contact-point-nr`
setting to the exact number of nodes the initial startup of the cluster will be done. For example, if starting a cluster with
4 nodes initially, and later scaling it out to many more nodes, be sure to set this setting to `4` for additional safety of
the initial joining, even in face of an flaky discovery mechanism!


### Recommended Configuration

When using the bootstrap module, there are some underlying Akka Cluster settings that should be specified to ensure
that your deployment is robust.

Since the target environments for this module are dynamic, that is, instances can come and go, failure needs to be
considered. The following configuration will result in your application being shut down after 30 seconds if it is unable to
join the discovered seed nodes. In this case, the orchestrator (i.e. Kubernetes or Marathon) will restart your node
and the operation will (presumably) eventually succeed. You'll want to specify the following in your `application.conf` file:

@@snip [CompileOnly.scala](/integration-test/local/src/main/resources/application.conf) { #coorindated-shutdown }

### Rolling updates

#### Graceful shutdown 

Akka Cluster can handle hard failures using a downing provider such as Lightbend's split brain resolver discussed below.
However this should not be relied upon for regular rolling redeploys. Features such as `ClusterSingleton`s and `ClusterSharding`
can safely restart actors on new nodes far quicker when it is certain that a node has shutdown rather than crashed. 

Graceful leaving will happen with the default settings as it is part of @extref:[Coordinated Shutdown](akka:actors.html#coordinated-shutdown). 
Just ensure that a node is sent a `SIGTERM` and not a `SIGKILL`. Environments such as Kubernetes will do this, it is important to ensure 
that if JVM is wrapped with a script that it forwards the signal. 

Upon receiving a `SIGTERM` Coordinated Shutdown will:

* Perform a `Cluster(system).leave` on itself
* The status of the member will be changed to Exiting while allowing any shards to be shutdown gracefully and 
  `ClusterSingleton`s to be migrated if this was the oldest node. Finally the node is removed from the Akka Cluster membership.
  

#### Number of nodes to redeploy at once

Akka bootstrap requires a `stable-period` where service discovery returns a stable set of contact points. When doing rolling
updates it is best to wait for a node (or group of nodes) to finish joining the cluster before adding and removing other nodes.

#### Cluster Singletons

`ClusterSingleton`s run on the oldest node in the cluster. To avoid singletons moving during every node deployment it is advised
to start a rolling redeploy starting at the newest node. Then `ClusterSingleton`s only move once. This is the default behaviour 
for Kubernetes deployments. Cluster Sharding uses a singleton internally so this is important even if not using singletons directly.


### Split brains and ungraceful shutdown

Nodes can crash causing cluster members to become unreachable. This is a tricky problem as it is not
possible to distinguish between a network partition and a node failure. To rectify this in an automated manner,
Lightbend provides @extref:[Split Brain Resolver](akka-enhancements:split-brain-resolver.html)
as a feature of the Lightbend Subscription. This module has a number of strategies that can ensure that the cluster
continues to function during network partitions and node failures.

## Bootstrap Recipes

To see how to configure and use bootstrap in various environments such as Kubernetes, see @ref[recipes](recipes.md).

@@@ index

* [details](details.md)
* [troubleshooting](troubleshooting.md)
* [local-config](local-config.md)
* [kuberntes-dns](kubernetes.md)
* [kuberntes-api](kubernetes-api.md)
* [istio](istio.md)

@@@
