# Kubernetes

As of Akka 2.5.15 and Akka-Management 0.18 the recommended way to run Akka Cluster in Kubernetes is to:

* Use Akka Bootstrap with `akka-dns` with cluster formation via DNS SRV records
* Use a headless service for internal for Akka management/bootstrap so that readiness probes for prod traffic don't interfere with bootstrap 
* If required use a separate service and/or ingress for user-facing endpoints, for example [HTTP](https://doc.akka.io/docs/akka-http/current/) or [gRPC](https://developer.lightbend.com/docs/akka-grpc/current/)

# Kubernetes Services

## Internal headless service

For Akka Cluster / Management use a headless service. This allows the solution to not be coupled to k8s as well
as there is no use case for load balancing across management/remoting ports.
Set endpoints to be published before readiness checks pass as these endpoints are required to bootstrap the Cluster
and make the application ready. 


```
apiVersion: v1
kind: Service
metadata:
  labels:
    appName: "akka-cluster-kubernetes"
  annotations:
    service.alpha.kubernetes.io/tolerate-unready-endpoints: "true"
  name: "akka-cluster-kubernetes"
spec:
  ports:
    - name: management
      port: 8558
      protocol: TCP
      targetPort: 8558
    - name: remoting
      port: 2552
      protocol: TCP
      targetPort: 2552
  selector:
    appName: "akka-cluster-kubernetes"
  clusterIP: None
  publishNotReadyAddresses: true
```

Note there are currently two ways to specify that addresses should be published if not ready, the initial way via an annotation 
`service.alpha.kubernetes.io/tolerate-unready-endpoints` and via the new officially supported way as the property `publishNotReadyAddresses`.
Set both as depending on your DNS solution it may have not migrated from the annotation to the property.

This will result in SRV records being published for the service that contain the nodes that are not ready. This allows
bootstrap to find them and form the cluster thus making them ready.

Then to configure your application:

```
akka {
  loglevel = DEBUG

  io.dns.resolver = async-dns

  actor {
    provider = "cluster"
  }

  management {
    cluster.bootstrap {
      contact-point-discovery {
        port-name = "management" # name of the port in the headless service
        protocol = "tcp" # protocol in the headless service
        service-name = "akka-cluster-kubernetes" # headless service name
        service-namespace = "default.svc.cluster.local" # your namespace/cluster name
      }
    }

    http {
      port = 8558
      bind-hostname = "0.0.0.0"
    }
  }

  remote {
    netty.tcp {
      port = 2552
    }
  }

  discovery {
    method = akka-dns
  }
}
```

The same configuration will work for any environment that has an SRV record for your Akka Clustered application. 

## External service 

For prod traffic e.g. HTTP use a regular service or an alternative ingress mechanism. 
With an appropriate readiness check this results in traffic not being routed until bootstrap has finished.

```
apiVersion: v1
kind: Service
metadata:
  labels:
    appName: "akka-cluster-kubernetes"
  name: "akka-cluster-kubernetes-public"
spec:
  ports:
    - name: http 
      port: 8080
      protocol: TCP
      targetPort: 8080 
  selector:
    appName: "akka-cluster-kubernetes"
```

This will result in a ClusterIP being created and only added to `Endpoints` when the pods are `ready`

Note that the `appName` is the same for both services as we want the services to point to the same pods just have
different service types and DNS behavior.


## Pods

Health checks can be used check a node is part of a cluster e.g.

```scala
class KubernetesHealthChecks(system: ActorSystem) {

  val cluster = Cluster(system)

  private val readyStates: Set[MemberStatus] = Set(MemberStatus.Up, MemberStatus.Down)
  private val aliveStates: Set[MemberStatus] = Set(MemberStatus.Joining, MemberStatus.WeaklyUp, MemberStatus.Up, MemberStatus.Leaving, MemberStatus.Exiting)

  val k8sHealthChecks: Route =
  concat(
    path("ready") {
      get {
        val selfState = cluster.selfMember.status
        if (readyStates.contains(selfState)) complete(StatusCodes.OK)
        else complete(StatusCodes.InternalServerError)
      }
    },
    path("alive") {
      get {
        val selfState = cluster.selfMember.status
        if (aliveStates.contains(selfState)) complete(StatusCodes.OK)
        else complete(StatusCodes.InternalServerError)
      }
    }
  )
}
```


This will mean that a pod won't get traffic until it is part of a cluster which is important
if `ClusterSharding` and `ClusterSingleton` are used.
