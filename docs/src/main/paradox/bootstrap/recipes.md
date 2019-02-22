# Bootstrap environments

A set of integration tests projects can be found in [integration-test folder of the Akka Management project](https://github.com/akka/akka-management/tree/master/integration-test).
These test various Akka management features together in various environments such as Kubernetes.

The following samples exist as standalone projects:

* [Akka Cluster bootstrap using the Kubernetes API with Java/Maven](https://github.com/akka/akka-sample-cluster-kubernetes-java)
* [Akka Cluster bootstrap using DNS in Kubernetes](https://github.com/akka/akka-sample-cluster-kubernetes-dns-java)

## Local

To run Bootstrap locally without any dependencies such as DNS or Kubernetes see the @ref[`local` example](local-config.md)

## Running Akka Cluster in Kubernetes

The goal of bootstrap is to support running Akka Cluster in Kubernetes as if it is a stateless application.
The part bootstrap solves is creating the initial cluster and handling scaling and re-deploys gracefully.

The recommended approach is to:

* Use a Deployment for creating the pods
* Use either the Kubernetes API or DNS for contact point discovery (details below)
* Optionally use a service or ingress for any for traffic coming from outside of the Akka Cluster e.g. gRPC and HTTP

### Kubernetes Deployment 

Use a regular deployment (not a StatefulSet) with the following settings. 

#### Update strategy

For small clusters it may make sense to set `maxUnavailable` to 0 and `maxSurge` to 1. 
This means that a new pod is created before removing any existing pods so if the new pod fails the cluster remains
at full strength until a rollback happens. For large clusters it may be too slow to do 1 pod at a time.

If using [SBR](https://developer.lightbend.com/docs/akka-commercial-addons/current/split-brain-resolver.html) have a `maxUnavailable` that will not cause downing

### Cluster singletons

Deployments order pods by pod state and then time spent ready when deciding which to remove first. This works well
with cluster singletons as they are typically removed last and then the cluster singletons move to the the oldest new pod.  

### External traffic

For production traffic e.g. HTTP use a regular service or an alternative ingress mechanism. 
With an appropriate readiness check this results in traffic not being routed until bootstrap has finished.

@@snip [akka-cluster.yml](/integration-test/kubernetes-dns/kubernetes/akka-cluster.yml)  { #public }

This will result in a ClusterIP being created and only added to `Endpoints` when the pods are `ready`

Note that the `app` is the same for both services as they both refer to the same pods. 

### Health checks

`akka-management` includes a HTTP route for readiness and liveness checks.
`akka-management-cluster-http` includes readiness check for the Akka Cluster membership. To use it
add the following dependency:

@@dependency[sbt,Gradle,Maven] {
  group=com.lightbend.akka.management
  artifact=akka-management-cluster-http_$scala.binary_version$
  version=$version$
}

The readiness check is exposed on the Akka Management port as a `GET` on `/ready` and the liveness check is a `GET` on `/alive`

Configure them to be used in your deployment:

@@snip [akka-cluster.yml](/integration-test/kubernetes-dns/kubernetes/akka-cluster.yml)  { #health }


This will mean that a pod won't get traffic until it is part of a cluster, which is important
if either `ClusterSharding` or `ClusterSingleton` are used. 


### Contact point discovery


Contact point discovery can use either `kubernetes` or `akka-dns` service discovery. Details
on additional resources required and how they work:

* @ref[Kubernetes using `kubernetes-api` discovery](kubernetes-api.md)
* @ref[Kubernetes using `akka-dns` discovery](kubernetes.md)

Kubernetes-api is the more battle tested mechanism, DNS was added in Akka 2.5.15 and Akka Management 0.18.
DNS has the benefit that it is agnostic of Kubernetes so does not require pods be able to communicate with the API
server. However it requires a headless service that supports the `publishNotReadyAddresses` feature. If your Kubernetes setup
does not support `publishNotReadyAddresses` yet then use the `kubernetes-api` discovery mechanism.

### Running the Kubernetes demos

The following steps work for the `integration-test/kubernetes-api` or the `integration-test/kubernetes-dns` sub-project:

To run the demo in a real Kubernetes or OpenShift cluster the images must be pushed to a registry that cluster
has access to and then `kubernetes/akka-cluster.yml` (in either sub-project) modified with the full image path.

The following shows how the sample can be run in a local cluster using either `minishift` or `minikube`. Unless
explicitly stated `minikube` can be replaced with `minishift` and `kubectl` with `oc` in any of the commands below.

Start [minikube](https://github.com/kubernetes/minikube) make sure you have installed and is running:

```
$ minikube start
```

Make sure your shell is configured to target the docker daemon running inside the VM:
 
```
$ eval $(minikube docker-env) 
```

Publish the application docker image locally. If running this project in a real cluster you'll need to publish the image to a repository
that is accessible from your Kubernetes cluster and update the `kubernetes/akka-cluster.yml` with the new image name.

```
$ sbt shell
> project integration-test-kubernetes-api (or integration-test-kubernetes-dns)
> docker:publishLocal 
```

The resources in `kubernetes/akka-cluster.yml` are configured to run in the `akka-bootstrap` namespace. Either change that to the namespace
you want to deploy to or ensure`akka-bootstrap` namespace exists either by creating it:

```
kubectl create namespace akka-bootstrap
```

Or if running with `minishift` creating a project called `akka-bootstrap`:

```
oc new-project akka-bootstrap
```

Next deploy the application:

```
# minikube using Kubernetes API
kubectl apply -f integration-test/kubernetes-api/kubernetes/akka-cluster.yml

or

# minikube using DNS
kubectl apply -f integration-test/kubernetes-dns/kubernetes/akka-cluster.yml

or

# minishift using Kubernetes API
oc apply -f integration-test/kubernetes-api/kubernetes/akka-cluster.yaml

or

# minishift using DNS
oc apply -f integration-test/kubernetes-dns/kubernetes/akka-cluster.yaml
```

This will create and start running a number of Pods hosting the application. The application nodes will form a cluster. 

In order to observe the logs during the cluster formation you can 
pick one of the pods and issue the kubectl logs command on it:

```
$ POD=$(kubectl get pods --namespace akka-bootstrap | grep akka-bootstrap | grep Running | head -n1 | awk '{ print $1 }'); echo $POD
akka-integration-test-bcc456d8c-6qx87

$ kubectl logs $POD --namespace akka-bootstrap --follow | less
```

```
[INFO] [12/13/2018 07:13:42.867] [main] [ClusterBootstrap(akka://default)] Initiating bootstrap procedure using akka.discovery.akka-dns method...
[DEBUG] [12/13/2018 07:13:42.906] [default-akka.actor.default-dispatcher-2] [TimerScheduler(akka://default)] Start timer [resolve-key] with generation [1]
[DEBUG] [12/13/2018 07:13:42.919] [default-akka.actor.default-dispatcher-2] [TimerScheduler(akka://default)] Start timer [decide-key] with generation [2]
[INFO] [12/13/2018 07:13:42.924] [default-akka.actor.default-dispatcher-2] [akka.tcp://default@172.17.0.7:2552/system/bootstrapCoordinator] Locating service members. Using discovery [akka.discovery.dns.DnsSimpleServiceDiscovery], join decider [akka.management.cluster.bootstrap.LowestAddressJoinDecider]
[INFO] [12/13/2018 07:13:42.933] [default-akka.actor.default-dispatcher-2] [akka.tcp://default@172.17.0.7:2552/system/bootstrapCoordinator] Looking up [Lookup(integration-test-kubernetes-dns-internal.akka-bootstrap.svc.cluster.local,Some(management),Some(tcp))]
[DEBUG] [12/13/2018 07:13:42.936] [default-akka.actor.default-dispatcher-2] [DnsSimpleServiceDiscovery(akka://default)] Lookup [Lookup(integration-test-kubernetes-dns-internal.akka-bootstrap.svc.cluster.local,Some(management),Some(tcp))] translated to SRV query [_management._tcp.integration-test-kubernetes-dns-internal.akka-bootstrap.svc.cluster.local] as contains portName and protocol
[DEBUG] [12/13/2018 07:13:42.995] [default-akka.actor.default-dispatcher-18] [akka.tcp://default@172.17.0.7:2552/system/IO-DNS] Resolution request for _management._tcp.integration-test-kubernetes-dns-internal.akka-bootstrap.svc.cluster.local Srv from Actor[akka://default/temp/$a]
[DEBUG] [12/13/2018 07:13:43.011] [default-akka.actor.default-dispatcher-6] [akka.tcp://default@172.17.0.7:2552/system/IO-DNS/async-dns/$a] Attempting to resolve _management._tcp.integration-test-kubernetes-dns-internal.akka-bootstrap.svc.cluster.local with Actor[akka://default/system/IO-DNS/async-dns/$a/$a#1272991285]
[DEBUG] [12/13/2018 07:13:43.049] [default-akka.actor.default-dispatcher-18] [akka.tcp://default@172.17.0.7:2552/system/IO-TCP/selectors/$a/0] Successfully bound to /0.0.0.0:8558
[DEBUG] [12/13/2018 07:13:43.134] [default-akka.actor.default-dispatcher-18] [akka.tcp://default@172.17.0.7:2552/system/IO-DNS/async-dns/$a/$a] Resolving [_management._tcp.integration-test-kubernetes-dns-internal.akka-bootstrap.svc.cluster.local] (SRV)
[INFO] [12/13/2018 07:13:43.147] [default-akka.actor.default-dispatcher-6] [AkkaManagement(akka://default)] Bound Akka Management (HTTP) endpoint to: 0.0.0.0:8558
[DEBUG] [12/13/2018 07:13:43.156] [default-akka.actor.default-dispatcher-5] [akka.tcp://default@172.17.0.7:2552/system/IO-TCP/selectors/$a/1] Successfully bound to /0.0.0.0:8080
[INFO] [12/13/2018 07:13:43.180] [main] [akka.actor.ActorSystemImpl(default)] Server online at http://localhost:8080/
....
[INFO] [12/13/2018 07:13:50.631] [default-akka.actor.default-dispatcher-5] [akka.cluster.Cluster(akka://default)] Cluster Node [akka.tcp://default@172.17.0.7:2552] - Welcome from [akka.tcp://default@172.17.0.6:2552]
[DEBUG] [12/13/2018 07:13:50.644] [default-akka.remote.default-remote-dispatcher-22] [akka.serialization.Serialization(akka://default)] Using serializer [akka.cluster.protobuf.ClusterMessageSerializer] for message [akka.cluster.GossipEnvelope]
[INFO] [12/13/2018 07:13:50.659] [default-akka.actor.default-dispatcher-18] [akka.tcp://default@172.17.0.7:2552/user/$b] Cluster akka.tcp://default@172.17.0.7:2552 >>> MemberUp(Member(address = akka.tcp://default@172.17.0.6:2552, status = Up))
[INFO] [12/13/2018 07:13:50.676] [default-akka.actor.default-dispatcher-20] [akka.tcp://default@172.17.0.7:2552/user/$b] Cluster akka.tcp://default@172.17.0.7:2552 >>> MemberJoined(Member(address = akka.tcp://default@172.17.0.7:2552, status = Joining))
[INFO] [12/13/2018 07:13:50.716] [default-akka.actor.default-dispatcher-6] [akka.tcp://default@172.17.0.7:2552/user/$b] Cluster akka.tcp://default@172.17.0.7:2552 >>> LeaderChanged(Some(akka.tcp://default@172.17.0.6:2552))
[INFO] [12/13/2018 07:13:50.720] [default-akka.actor.default-dispatcher-3] [akka.tcp://default@172.17.0.7:2552/user/$b] Cluster akka.tcp://default@172.17.0.7:2552 >>> RoleLeaderChanged(dc-default,Some(akka.tcp://default@172.17.0.6:2552))
[INFO] [12/13/2018 07:13:50.727] [default-akka.actor.default-dispatcher-6] [akka.tcp://default@172.17.0.7:2552/user/$b] Cluster akka.tcp://default@172.17.0.7:2552 >>> SeenChanged(true,Set(akka.tcp://default@172.17.0.6:2552, akka.tcp://default@172.17.0.7:2552))
[INFO] [12/13/2018 07:13:50.733] [default-akka.actor.default-dispatcher-5] [akka.tcp://default@172.17.0.7:2552/user/$b] Cluster akka.tcp://default@172.17.0.7:2552 >>> ReachabilityChanged()
```
