# Kubernetes

A full working example that can be deployed to kubernetes via `minikube` is in `bootstrap-demo/kubernetes-dns`.

As of Akka 2.5.15 and Akka-Management 0.18 `akka-dns` discovery mechanism can be used to deploy Akka Cluster in Kubernetes.
This example shows how to:

* Use Akka Bootstrap with `akka-dns` with cluster formation via DNS SRV records
* Use a headless service for internal and Akka management/bootstrap so that readiness probes for prod traffic don't interfere with bootstrap 
    * Note that this requires the use of the `publishNotReadyAddresses`, which replaces the `service.alpha.kubernetes.io/tolerate-unready-endpoints: "true"` annotation , so bootstrap can see the pods before they are ready. Check your Kubernetes environment supports this feature
* If required use a separate service and/or ingress for user-facing endpoints, for example [HTTP](https://doc.akka.io/docs/akka-http/current/) or [gRPC](https://developer.lightbend.com/docs/akka-grpc/current/)

## Deployments

Use a regular deployment (not a StatefulSet). 

### Update strategy

For small clusters it may make sense to set `maxUnavailable` to 0 and `maxSurge` to 1. 
This means that a new pod is created before removing any existing pods so if the new pod fails the cluster remains
at full strength until a rollback happens. For large clusters it may be too slow to do 1 pod at a time.

If using [SBR](https://developer.lightbend.com/docs/akka-commercial-addons/current/split-brain-resolver.html) have a `maxUnavailable` that will not cause downing

### Cluster singletons

Deployments orders pods by pod state and then time spent ready when deciding which to remove first. The pod 
with cluster singletons is typically removed last and then the cluster singletons move the the oldest new pod.  


### Health checks

Health checks can be used check a node is part of a cluster e.g.

@@snip [health-checks]($management$/bootstrap-demo/kubernetes-dns/src/main/scala/akka/cluster/bootstrap/KubernetesHealthChecks.scala)  { #health }

This will mean that a pod won't get traffic until it is part of a cluster, which is important
if either `ClusterSharding` or `ClusterSingleton` are used.

## Services

### Internal headless service

For Akka Cluster / Management use a headless service. This allows the solution to not be coupled to k8s as well
as there is no use case for load balancing across management/remoting ports.
Set endpoints to be published before readiness checks pass as these endpoints are required to bootstrap the Cluster
and make the application ready. 


@@snip [akka-cluster.yml]($management$/bootstrap-demo/kubernetes-dns/kubernetes/akka-cluster.yml)  { #headless }

Note there are currently two ways to specify that addresses should be published if not ready, the initial way via an annotation 
`service.alpha.kubernetes.io/tolerate-unready-endpoints` and via the new officially supported way as the property `publishNotReadyAddresses`.
Set both as depending on your DNS solution it may have not migrated from the annotation to the property.

This will result in SRV records being published for the service that contain the nodes that are not ready. This allows
bootstrap to find them and form the cluster thus making them ready.

Then to configure your application:

@@snip [application.conf]($management$/bootstrap-demo/kubernetes-dns/src/main/resources/application.conf)  

The same configuration will work for any environment that has an SRV record for your Akka Clustered application. 

### External service 

For prod traffic e.g. HTTP use a regular service or an alternative ingress mechanism. 
With an appropriate readiness check this results in traffic not being routed until bootstrap has finished.

@@snip [akka-cluster.yml]($management$/bootstrap-demo/kubernetes-dns/kubernetes/akka-cluster.yml)  { #public }

This will result in a ClusterIP being created and only added to `Endpoints` when the pods are `ready`

Note that the `appName` is the same for both services as we want the services to point to the same pods just have
different service types and DNS behavior.


