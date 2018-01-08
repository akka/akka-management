<a id="bootstrap"></a>
# Akka Cluster Bootstrap

The bootstrap module / extension allows an Akka Cluster to (semi) automatically discover its neighbouring nodes,
and join the current node to them if a cluster already exists, or safely form a new cluster for all those nodes,
if no cluster exists there yet.

While bootstrap processes may be configured to use various implementations, the preferred, default (and currently only),
implementation utilises DNS records and so-called Contact Points on the target nodes to form the cluster. This works 
particularity well in environments like Kubernetes or Mesos where DNS records are managed for Services automatically.
Please note that unlike many solutions that have been proposed by the wider community, this solution does NOT require
any additional system like etcd/zookeeper/consul to be run along side the Akka cluster in order to discover the seed-nodes.

## Dependencies

The Akka Bootstrap extension consists of modular parts which handle steps of the bootstrap process.
n order to use it you need to depend on `akka-management-cluster-bootstrap` and a specific `akka-discovery` 
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

You also have to explicitly configure it to be used as the default implementation in your `application.conf`:

```
akka.discovery.method = akka-dns 
```

Be sure to read about the @ref[alternative discovery methods](discovery.md) that Akka provides, such that you
can pick the one most fitting to your cluster manager or runtime -- such as Kubernetes, Mesos or other cloud or tool specific methods.

## Akka Cluster Bootstrap Process explained

The Akka Cluster Bootstrap process is composed of two phases. First, a minimum number of Contact Points need to be gathered. 
Currently it will look for `akka.cluster.bootstrap.contact-point-discovery.service-name` appended
with `akka.cluster.bootstrap.contact-point-discovery.service-namespace` (if present) A records in DNS. In Kubernetes managed 
systems these would be available by default and list all `Pods` in a given `Service`. Those addresses are then contacted,
in what is referred to the Contact Point Probing procedure. Note that at this point the node has not joined any cluster yet.
The Contact Points are contacted using an alternative protocol which does not need membership, such as HTTP by default.

In this moment, we have multiple nodes probing each other's Contact Points. And if a contact point responds with
a known set of seed nodes, the probing node will join those. This can be seen as "epidemic" joining. Since that node will,
once it has completed joining, also start advertising those seed nodes using its own Contact Point, so any other node that
has not yet joined, but is probing this node, would get this information and join the existing cluster.

In the case no cluster exists yet -- the initial bootstrap of a cluster -- nodes will keep probing one another for a while
(`akka.cluster.bootstrap.contact-point.no-seeds-stable-margin`) and once that time margin passes, they will decide that 
no cluster exists, and one of the seen nodes should join *itself* to become the first node of a new cluster. It is of utmost
importance that only one node joins itself, so this decision has to be made deterministically. Since we know the addresses
of all Contact Points, and the contact points relate 1:1 to a Akka Remoting (Akka Cluster) address of the given node,
we're able to use this information to make a deterministic decision, without coordination across these nodes, as to which
of them should perform this join. We make this decision by sorting the known addresses from lowest to highest, and the
*lowest* address joins itself. It will then start advertising itself as seed node in it's Contact Point, which other nodes
will notice and start joining this node. Now the process just explained in the previous paragraph, referred to as "epidemic 
joining" continues until all nodes have joined the cluster. 

The bootstrap process can be roughly explained in two situations, one being when no cluster exists in the deployment
at all yet (which we call the "initial bootstrap) and the other case being when a cluster already exists and we're 
simply adding new nodes to it wanting them to discover and join that cluster.

Short description of the bootstrap process:

- New node is started, we call it "X",
- The node discovers its "neighbours" using akka-discovery (e.g. using DNS),
    - This is NOT enough to safely join or form a cluster, some initial negotiation between the nodes must take place.
- The node starts to probe the Contact Points of the discovered nodes
- Case 1) None of the contacted nodes return any seed nodes during the probing process,
    - The `no-seeds-stable-margin` timeout passes, and no discovery changes are are observed as well
    - The nodes all decide (autonomously) that no cluster exists, and a new one should be formed,
      they know all their addresses, and decide that the "lowest" sorted address is to start forming the cluster,
    - The lowest address node (e.g. "A") notices the same, and makes the decision to *join itself*,
    - Other nodes, including X, will notice that A has started returning *itself* as a seed-node in the Contact Point responses,
    - Any node, including X, immediately joins such seed node that it has observed in the Contact Point process.
    - Nodes continue probing the other nodes, and eventually will notice any of the existing nodes that are part of the cluster,
      and immediately join it. This phase is referred to as "epidemic joining".
    - Eventually all nodes have joined the same cluster, the process is complete.
- Case 2) A cluster exists already, and when probing the various nodes node X will receive at least one seed-node address,
from the contact points. 
    - The node joins any discovered (via Contact Points probing) seed-node and immediately becomes part of the cluster.
    - The process is complete, the node has successfully joined the "right" cluster.
        - Notice that this phase is exactly the same as the "epidemic joining" in the more complicated Case 1 when a new 
          cluster has to be formed.

In the next sections we explain the process in more detail.

### Case 1: "Initial" Bootstrap process

Both cases need to discover neighbouring nodes, which they do via Akka Discovery,
which we recommend (and implement) with DNS, though you may use alternative lookup methods if you want. The bootstrap 
process will then start probing those nodes Contact Points (which are HTTP endpoints, exposed via Akka Management by the
Bootstrap Management Extension) for known seeds to join. Since no cluster exists yet, the seed node replies will be 
empty from all contact points. Once at least `akka.management.cluster.bootstrap.required-contact-point-nr` nodes are
discovered and `akka.management.cluster.bootstrap.stable-margin` time has passed, the nodes will decide that it is time 
to confidently (enough) form a new cluster. The node with the "lowest" contact point address will decide to join itself,
and other nodes are expecting this awaiting a seed-node address to appear in the contact point probing responses.

Once the lowest addressed node has joined itself, it has formed a new cluster, and therefore starts returning itself
as seed-node in its Contact Point response. Other nodes notice this and will join it, growing the existing cluster.

The illustration below may be of help in visualising this process:

![project structure](images/bootstrap-forming-cluster.png)

The reason to not use Akka's remoting in the contact point probing itself is to in the future enable upgrades semi-automatic between even not wire compatible binary versions or protocols (e.g. moving from a classic remoting based system to an artery based one), or even more advanced deployment patterns.

### Case 2: Bootstrap process, with existing cluster

The Bootstrap process in face of an existing cluster in a deployment is very simple, and actually if you read through
Case 1, you already seen it in action.

The process starts the same way for a new node being started and wanting to discovery which cluster it should join.
It discovers its neighbours using Akka Discovery (e.g. by DNS), and starts probing their contact points.
As a cluster exists already, at-least-one node will return a non-empty seed-node list in its contact point reply.
The being bootstraped node immediately decides to join that seed-node, as that simply indicated that a cluster exists 
and the new node should be joining it. This marks the end of the process.

The observant reader may have noticed, that this is exactly what all nodes in Case 1 were doing as well, yet in that case
the additional decision of one node joining "itself" was made, kicking off the entire epidemic joining process from
there on.

![project structure](images/bootstrap-existing-cluster.png)


### Specific edge-cases explained

It is important to realise no *dynamic and automatic* cluster joining solution provides 100% safety, however the process
presented here is very close to it. Please note that the often used claim of using a consistent data store for the 
seed-nodes also is not 100% safe (sic!), since races could occur between the node having discovered a node from the strongly 
consistent store and attempting the join operation.

The here proposed solution is prone to very few and rather rare races. Built-in protection against the race cases exists
in the form of the stable timeout, which means that if any changes are being observed in discovery, the decision making
is delayed until the observation is stable again. This prevents initiating joining while discovery is still inconsistent.

Note also that the bootstrap process does NOT rely on full consistency of the discovery mechanism when adding new nodes 
to an existing cluster. This is very desirable, since this situation usually occurs when dynamically scaling up due to 
increased load on your service, and some services may indeed not be fully consistent then. However, the Akka Cluster 
membership protocol IS strongly consistent, and it is the source of truth with regards what the cluster is consisting of,
and no external system can have more reliable information about this (since it could be outdated). This is why the 
Contact Point probing mechanism exists, and even if discovery would only return *partial* or even *different set of nodes
for each lookup* the probing would allow the node still to join all the right nodes, thanks to how Akka Cluster's membership
and gossip protocols work. Summing up, the bootstrap mechanism works well for adding nodes to the system, even under load,
even if the DNS system is not completely consistent. 

If however we are talking about an inconsistent DNS lookup response during the Initial Bootstrap, the nodes will be delayed
forming the cluster as they expect the lookups to be consistent, this is checked by the stable-margin configuration option.

For complete safety of the Initial Bootstrap it is recommended to set the `akka.cluster.bootstrap.required-contact-point-nr` 
setting to the exact number of nodes the initial startup of the cluster will be done. For example, if starting a cluster with
4 nodes initially, and later scaling it out to many more nodes, be sure to set this setting to `4` for additional safety of
the initial joining, even in face of an flaky discovery mechanism!

@@@ note
  It *is* crucial for the nodes to have a consistent view of their neighbours for the Initial Bootstrap.
@@@


## Joining mechanism precedence

As Akka Cluster allows nodes to join to a cluster using a few methods it is the precedence of each method
is strictly defined and is as follows:

- If configured `akka.cluster.seed-nodes` (in your `application.conf`) are non-empty, those nodes will be joined, and bootstrap will NOT execute even if `start()` is called (a warning will be logged though).
- If an explicit `cluster.join` or `cluster.joinSeedNodes` is invokes before the bootstrap completes that
 joining would take precedence over the bootstrap. *This is however **not** recommended to mix multiple
 joining methods like this.*
- The Cluster Bootstrap mechanism takes some time to complete, but eventually issues a `join`.

@@@ warning
  It is NOT recommended to mix various joining mechanisms. Pick one mechanism and stick to it in order to
  avoid any surprises during cluster formation. E.g. do NOT set `akka.cluster.seed-nodes` if you are going
  to be using the Bootstrap mechanism. 
@@@

## Examples

### Kubernetes example

In Kubernetes, one would deploy an Akka Cluster as a single [Headless Service](https://kubernetes.io/docs/concepts/services-networking/service/#headless-services).

An example application using docker and prepared to be deployed to kubernetes is provided in Akka Management's github repository 
as sub-project [bootstrap-joining-demo](https://github.com/akka/akka-management/tree/master/bootstrap-joining-demo/kubernetes-api).

Rather than configuring the Dockerfile directly, we used the [sbt-native-packager](http://sbt-native-packager.readthedocs.io/en/stable/) 
to package the application as docker container. See the `build.sbt` file for more details, and the `kubernetes/akka-cluster.yml` 
file for the service configuration, which is:

@@snip [akka-cluster.yml](../../../../bootstrap-joining-demo/kubernetes-api/kubernetes/akka-cluster.yml) 

In order to run the example you can build it via:

```
sbt shell
> project bootstrap-joining-demo
> docker:publishLocal
```

Next, you'll want to run the example using [minikube](https://github.com/kubernetes/minikube) (or a real kubernetes system),
which you can do by typing:

```
# 1) make sure you have installed `minikube` (see link above)
```

```
# 2) make sure minikube is running
$ minikube start
Starting local Kubernetes v1.8.0 cluster...
Starting VM...
Getting VM IP address...
Moving files into cluster...
Setting up certs...
Connecting to cluster...
Setting up kubeconfig...
Starting cluster components...
Kubectl is now configured to use the cluster.
```

```
# 3) make sure your shell is configured to target minikube cluster
$ eval $(minikube docker-env) 
```

```
# 4) Publish the application docker image locally:
$ sbt shell
...
> project bootstrap-joining-demo-kubernetes-api
... 
> docker:publishLocal 
...
[info] Successfully tagged ktoso/akka-management-bootstrap-joining-demo-kubernetes-api:1.3.3.7
[info] Built image ktoso/akka-management-bootstrap-joining-demo-kubernetes-api:1.3.3.7
[success] Total time: 25 s, completed Dec 8, 2017 7:47:05 PM
```

Once the image is published, you can deploy it onto the kubernetes cluster by calling:

@@snip [kube-create.sh](../../../../bootstrap-joining-demo/kubernetes-api/kube-create.sh) 

This will create and start running a number of Pods hosting the application. The application nodes will proceed with 
forming the cluster using the DNS bootstrap method. In order to observe the logs during the cluster formation you can 
pick one of the pods and simply issue the kubectl logs command on it, like this:

```
$ POD=$(kubectl get pods | grep appka | grep Running | head -n1 | awk '{ print $1 }'); echo $POD
appka-6bfdf47ff6-l7cpb

$ kubectl logs $POD -f
...
[INFO] [12/08/2017 10:57:52.678] [main] [akka.remote.Remoting] Starting remoting
...
[INFO] [12/08/2017 10:57:53.597] [main] [akka.remote.Remoting] Remoting started; listening on addresses :[akka.tcp://Appka@172.17.0.2:2552]
...
[INFO] [12/08/2017 10:58:00.558] [main] [ClusterHttpManagement(akka://Appka)] Bound akka-management HTTP endpoint to: 172.17.0.2:19999
[INFO] [12/08/2017 10:58:00.559] [main] [ClusterBootstrap(akka://Appka)] Initiating bootstrap procedure using akka.discovery.akka-dns method...
...
[INFO] [12/08/2017 10:58:04.747] [Appka-akka.actor.default-dispatcher-2] [akka.tcp://Appka@172.17.0.2:2552/system/headlessServiceDnsBootstrap] Initiating new cluster, self-joining [akka.tcp://Appka@172.17.0.2:2552], as this node has the LOWEST address out of: [List(ResolvedTarget(172.17.0.2,None), ResolvedTarget(172.17.0.6,None), ResolvedTarget(172.17.0.4,None), ResolvedTarget(172.17.0.3,None))]! Other nodes are expected to locate this cluster via continued contact-point probing.
[INFO] [12/08/2017 10:58:04.796] [Appka-akka.actor.default-dispatcher-15] [akka.cluster.Cluster(akka://Appka)] Cluster Node [akka.tcp://Appka@172.17.0.2:2552] - Node [akka.tcp://Appka@172.17.0.2:2552] is JOINING, roles [dc-default]
[INFO] [12/08/2017 10:58:04.894] [Appka-akka.actor.default-dispatcher-15] [akka.cluster.Cluster(akka://Appka)] Cluster Node [akka.tcp://Appka@172.17.0.2:2552] - Leader is moving node [akka.tcp://Appka@172.17.0.2:2552] to [Up]
[INFO] [12/08/2017 10:58:04.920] [Appka-akka.actor.default-dispatcher-16] [akka.tcp://Appka@172.17.0.2:2552/user/$a] Cluster akka.tcp://Appka@172.17.0.2:2552 >>> MemberUp(Member(address = akka.tcp://Appka@172.17.0.2:2552, status = Up))
...
```

Which concludes the short demo of cluster bootstrap in kubernetes using the DNS discovery mechanism.
