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

## Akka Cluster Bootstrap Process explained

The Akka Cluster Bootstrap process is composed of two phases. First, a minimum number of Contact Points (by default at least 
2) need to be gathered. Currently it will look for `akka.cluster.bootstrap.contact-point-discovery.service-name` appended
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

In summary, the process is as follows:
 - find Contact Points using DNS
 - start probing each of the Contact Points for their known seed-nodes
 - a) Cluster already exists
   - if any of the Contact Points returns known seed-nodes, join them immediately
   - the node is now part of the cluster, mission complete
   - each time a node is added to the cluster, it is included in the seed-nodes (with a maximum limit of a few nodes),
     which causes the joining to be spread out to the nodes which are already part of the cluster. They also start 
     advertising see-nodes, so if a new node contacts any of those fresly joined nodes, they join the same cluster.
 - b) No cluster exists:
   - if none of the contact points returns any seed nodes
   - nodes will after a timeout realise they should form a new cluster, 
     and will decide that the "lowest" address should join itself. 
   - this deterministic decision causes one of the nodes to join itself, and start advertising itself in it's Contact Point 
     as seed-node
   - other nodes notice this via contact point probing and join this node
   - from here the process explained in "Cluster already exists" continues as explained in the "epidemic joining" process,
     until all nodes have joined the cluster.
 

### Specific edge-cases explained

TODO: There are specific very hard to cause edge cases in the self-join. It is important to realise that using a consistent 
store would not help with these as well. The race exists regardless of how we obtain the list of nodes.

### Discussion: Rationale for avoidance of external (consistent) data-store for seed-nodes

TODO: explain that those only provide the illusion of safety, as races can still happen in the "self join",
even if a consistent data-store is used. It is only about probability of the issue happening, and with our solution
we're as good as the same without the need for external stores other than DNS.

TODO: explain that by forcing users to run a consensus cluster in order to even join members to another cluster is much 
operational overhead, and not actually required.

TODO: explain that a consistency picking data-store is NOT optimal for systems which need to contact such store to JOIN
a cluster. After all, you want to join new nodes perhaps when the system is under much load and even the consensus system
could then be overloaded -- causing you to be unable to join new nodes! By embracing a truly peer-to-peer joining model,
we can even join nodes (yes, safely) during intense traffic and avoid having one more system that could break. 

## Joining mechanism precedence

As Akka Cluster allows nodes to join to a cluster using a few methods it is the precedence of each method
is strictly defined and is as follows:

- If configured `akka.cluster.seed-nodes` (in your `application.conf`) are non-empty, those nodes will be joined, and bootstrap will NOT execute even if `start()` is called (a warning will be logged though).
- If an explicit `cluster.join` or `cluster.joinSeedNodes` is invokes before the bootstrap completes that
 joining would take precedence over the bootstrap. *This is however **not** recommended to mix multiple
 joining methods like this. Stick to one joining mechanism to avoid surprising behaviour during cluster
 formation.*
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
as sub-project [bootstrap-joining-demo](https://github.com/akka/akka-management/tree/master/bootstrap-joining-demo).

Rather than configuring the Dockerfile directly, we used the sbt-native-packager to package the application as docker container.
See the `build.sbt` file for more details, and the `kubernetes/akka-cluster.yml` file for the service configuration, which is:

@@snip [akka-cluster.yml](../../../../bootstrap-joining-demo/kubernetes/akka-cluster.yml) 

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
> project bootstrap-joining-demo
... 
> docker:publishLocal 
...
[info] Successfully tagged ktoso/akka-management-bootstrap-joining-demo:1.3.3.7
[info] Built image ktoso/akka-management-bootstrap-joining-demo:1.3.3.7
[success] Total time: 25 s, completed Dec 8, 2017 7:47:05 PM
```

Once the image is published, you can deploy it onto the kubernetes cluster by calling:

@@snip [kube-create.sh](../../../../bootstrap-joining-demo/kube-create.sh) 

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
