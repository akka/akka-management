# Bootstrap recipes

A set of bootstrap demonstration projects can be found in [bootstrap-demo](https://github.com/akka/akka-management/tree/master/bootstrap-demo) folder of this project. Currently there are projects for:

* Kubernetes using the API server
* Kubernetes using DNS
* Local using config
* AWS API
* Mesos using DNS
* Marathon

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

@@snip [akka-cluster.yml](/bootstrap-demo/kubernetes-dns/kubernetes/akka-cluster.yml)  { #public }

This will result in a ClusterIP being created and only added to `Endpoints` when the pods are `ready`

Note that the `app` is the same for both services as they both refer to the same pods. 

### Health checks

`akka-cluster-http-management` includes an Akka Management route provider for a readiness and a liveness check. To use it
add the following dependency:

@@dependency[sbt,Gradle,Maven] {
  group=com.lightbend.akka.management
  artifact=akka-management_$scala.binary_version$
  version=$version$
}

The readiness check is exposed on the Akka Management port as a `GET` on `/ready` and the liveness check is a `GET` on `/alive`

Configure them to be used in your deployment:

@@snip [akka-cluster.yml](/bootstrap-demo/kubernetes-dns/kubernetes/akka-cluster.yml)  { #health }


This will mean that a pod won't get traffic until it is part of a cluster, which is important
if either `ClusterSharding` or `ClusterSingleton` are used. 


### Contact point discovery


Contact point discovery can use either `kubernetes` or `akka-dns` service discovery can be used. Details
on additional resources required and how they work:

* @ref[Kubernetes using `kubernetes-api` discovery](kubernetes-api.md)
* @ref[Kubernetes using `akka-dns` discovery](kubernetes.md)

Kubernetes-api is the more battle tested mechanism, DNS was added in Akka 2.5.15 and Akka Management 0.18.
DNS has the benefit that it is agnostic of Kubernetes so does not require pods be able to communicate with the API
server. However it requires a headless service that supports the `publishNotReadyAddresses` feature. If your Kubernetes setup
does not support `publishNotReadyAddresses` yet then use the `kubernetes-api` discovery mechanism.

### Running the Kubernetes demos

The following steps work for the `bootstrap-demo/kubernetes-api` or the `bootstrap-demo/kubernetes-dns` sub-project:

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
> project bootstrap-demo-kubernetes-api (or bootstrap-demo-kubernetes-dns)
> docker:publishLocal 
```

The resources in `kubernetes/akka-cluster.yml` are configured to run in the `akka-bootstrap` namespace. Either change that to the namespace
you want to deploy to or ensure`akka-bootstrap` namespace exists either by creating it:

```
kubectl create namespace akka-boostrap
```

Or if running with `minishift` creating a project called `akka-bootstrap`:

```
oc new-project akka-bootstrap
```

Next deploy the application:

```
kubectl apply -f kubernetes/akka-cluster.yml

or

oc apply -f kubernetes/akka-cluster.yaml
```

This will create and start running a number of Pods hosting the application. The application nodes will form a cluster. 

In order to observe the logs during the cluster formation you can 
pick one of the pods and issue the kubectl logs command on it:

```
$ POD=$(kubectl get pods | grep appka | grep Running | head -n1 | awk '{ print $1 }'); echo $POD
appka-6bfdf47ff6-l7cpb

$ kubectl logs $POD -f
```

```
[INFO] [09/11/2018 09:57:16.612] [Appka-akka.actor.default-dispatcher-4] [akka.tcp://Appka@172.17.0.14:2552/system/bootstrapCoordinator] Locating service members. Using discovery [akka.discovery.kubernetes.KubernetesApiSimpleServiceDiscovery], join decider [akka.management.cluster.bootstrap.LowestAddressJoinDecider]                                                                
[INFO] [09/11/2018 09:57:16.613] [Appka-akka.actor.default-dispatcher-4] [akka.tcp://Appka@172.17.0.14:2552/system/bootstrapCoordinator] Looking up [Lookup(appka-service.default.svc.cluster.local,Some(management),Some(tcp))]                                                                                                                                                             
[INFO] [09/11/2018 09:57:16.713] [Appka-akka.actor.default-dispatcher-19] [AkkaManagement(akka://Appka)] Bound Akka Management (HTTP) endpoint to: 172.17.0.14:8558
[INFO] [09/11/2018 09:57:16.746] [Appka-akka.actor.default-dispatcher-17] [akka.actor.ActorSystemImpl(Appka)] Querying for pods with label selector: [actorSystemName=appka]
[INFO] [09/11/2018 09:57:17.710] [Appka-akka.actor.default-dispatcher-3] [HttpClusterBootstrapRoutes(akka://Appka)] Bootstrap request from 172.17.0.15:55502: Contact Point returning 0 seed-nodes ([Set()])                                                                                                                                                                                 
[INFO] [09/11/2018 09:57:17.718] [Appka-akka.actor.default-dispatcher-17] [akka.tcp://Appka@172.17.0.14:2552/system/bootstrapCoordinator] Located service members based on: [Lookup(appka-service.default.svc.cluster.local,Some(management),Some(tcp))]: [ResolvedTarget(172-17-0-14.default.pod.cluster.local,Some(8558),Some(/172.17.0.14)), ResolvedTarget(172-17-0-15.default.pod.cluster
.local,Some(8558),Some(/172.17.0.15))]
[INFO] [09/11/2018 09:57:23.636] [Appka-akka.actor.default-dispatcher-17] [akka.tcp://Appka@172.17.0.14:2552/system/bootstrapCoordinator] Initiating new cluster, self-joining [akka.tcp://Appka@172.17.0.14:2552]. Other nodes are expected to locate this cluster via continued contact-point probing.                                                                                     
[INFO] [09/11/2018 09:57:23.650] [Appka-akka.actor.default-dispatcher-19] [akka.cluster.Cluster(akka://Appka)] Cluster Node [akka.tcp://Appka@172.17.0.14:2552] - Node [akka.tcp://Appka@172.17.0.14:2552] is JOINING itself (with roles [dc-default]) and forming new cluster                                                                                                               
[INFO] [09/11/2018 09:57:23.655] [Appka-akka.actor.default-dispatcher-19] [akka.cluster.Cluster(akka://Appka)] Cluster Node [akka.tcp://Appka@172.17.0.14:2552] - Cluster Node [akka.tcp://Appka@172.17.0.14:2552] dc [default] is the new leader                                                                                                                                            
[INFO] [09/11/2018 09:57:23.676] [Appka-akka.actor.default-dispatcher-19] [akka.cluster.Cluster(akka://Appka)] Cluster Node [akka.tcp://Appka@172.17.0.14:2552] - Leader is moving node [akka.tcp://Appka@172.17.0.14:2552] to [Up]                                                                                                                                                          
[INFO] [09/11/2018 09:57:23.680] [Appka-akka.actor.default-dispatcher-17] [akka.actor.ActorSystemImpl(Appka)] Cluster member is up!

```

