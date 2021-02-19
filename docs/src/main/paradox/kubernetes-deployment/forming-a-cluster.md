# Forming an Akka Cluster

Services that use Akka Cluster have additional requirements over stateless applications.
To form a cluster, each pod needs to know which other pods have been deployed as part of that service, 
so that they can connect to each other. Akka provides a Cluster Bootstrap library that allows Akka applications in Kubernetes to 
discover this automatically using the Kubernetes API. The process is roughly as follows:

1. When the application starts, the application polls the Kubernetes API to find what pods are deployed, 
   until a configured minimum number of pods have been discovered.
2. It then attempts to connect to those pods, using Akka's HTTP management interface, and queries whether any of those pods have already formed a cluster.
3. If a cluster has already been formed, then the application will join the cluster.
4. If a cluster has not yet been formed on any of the pods, a deterministic function is used to decide which pod will initiate the cluster -
   this function ensures that all pods that are currently going through this process will decide on the same pod.
5. The pod that is decided to start the cluster forms a cluster with itself.
6. The remaining pods poll that pod until it reports that it has formed a cluster, they then join it.

For a much more detailed description of this process, see the [Akka Cluster Bootstrap documentation](https://developer.lightbend.com/docs/akka-management/current/bootstrap/details.html).

## Bootstrap and Management dependencies

Add the following dependencies to your application:

* Akka Management Cluster HTTP: This provides HTTP management endpoints as well as a @ref[cluster health check](../cluster-http-management.md#health-checks)
* @ref[Akka Discovery Kubernetes](../discovery/kubernetes.md): This provides a discovery mechanism that queries the Kubernetes API
* @ref[Akka Bootstrap](../bootstrap/index.md): This bootstraps the cluster from nodes discovered via the Kubernetes API

@@dependency[sbt,Gradle,Maven] {
  symbol1=AkkaManagementVersion
  value1=$project.version$
  group=com.lightbend.akka.management
  artifact=akka-management-cluster-http_$scala.binary.version$
  version=AkkaManagementVersion
  group2=com.lightbend.akka.management
  artifact2=akka-management-cluster-bootstrap_$scala.binary.version$
  version2=AkkaManagementVersion
  group3="com.lightbend.akka.discovery"
  artifact3="akka-discovery-kubernetes-api_$scala.binary.version$"
  version3=AkkaManagementVersion
}

## Configuring Cluster Bootstrap

There are three components that need to be configured: Akka Cluster, Akka Management HTTP, and Akka Cluster Bootstrap.

### Akka Cluster

Set three things for Akka Cluster:

* Set the actor provider to `cluster`.
* Shutdown if cluster formation doesn't work. This will cause Kubernetes to re-create the pod.
* Exit the JVM on ActorSystem termination to allow Kubernetes to re-create it.

```HOCON
akka {
    actor {
        provider = cluster
    }

    cluster {
        shutdown-after-unsuccessful-join-seed-nodes = 60s
    }
    coordinated-shutdown.exit-jvm = on
}
```
### Akka Management HTTP

The default configuration for Akka management HTTP is suitable for use in Kubernetes, it will bind to a default port of 8558 on the pods external IP address.

### Akka Cluster Bootstrap

To configure Cluster Bootstrap, we need to tell it which discovery method will be used to discover the other nodes in the cluster. 
This uses Akka Discovery to find nodes, however, the discovery method and configuration used in Cluster Bootstrap will often be different 
to the method used for looking up other services. The reason for this is that during Cluster Bootstrap, we are interested in discovering 
nodes even when they aren't ready to handle requests yet, for example, because they too are trying to form a cluster. If we were to use a 
method such as DNS to lookup other services, the Kubernetes DNS server, by default, will only return services that are ready to serve requests, 
indicated by their readiness check passing. Hence, when forming a new cluster, there is a chicken or egg problem, Kubernetes won't tell us which 
nodes are running that we can form a cluster with until those nodes are ready, and those nodes won't pass their readiness check until they've formed a cluster.

Hence, we need to use a different discovery method for Cluster Bootstrap, and for Kubernetes, the simplest method is to 
use the Kubernetes API, which will return all nodes regardless of their readiness state. 

```
akka.management {
  cluster.bootstrap {
    contact-point-discovery {
      discovery-method = kubernetes-api
    }
  }
}
```

You can optionally specify a `service-name` otherwise the name of the AkkaSystem is used that matches your label in the deployment spec.


## Starting

To ensure that Cluster Bootstrap is started, both the Cluster Bootstrap and the Akka Management extensions must be started. 
This can be done by invoking the `start` method on both the `ClusterBoostrap` and `AkkaManagement` extensions when your application starts up.

Scala
:  @@snip [CompileOnly.scala](/cluster-bootstrap/src/test/scala/doc/akka/management/cluster/bootstrap/ClusterBootstrapCompileOnly.scala) { #start }

Java
:  @@snip [CompileOnly.java](/cluster-bootstrap/src/test/java/jdoc/akka/management/cluster/bootstrap/ClusterBootstrapCompileOnly.java) { #start }

## Role-Based Access Control

By default, pods are unable to use the Kubernetes API because they are not authenticated to do so. 
In order to allow the applications pods to form an Akka Cluster using the Kubernetes API, we need to define some Role-Based Access Control (RBAC) roles and bindings.

RBAC allows the configuration of access control using two key concepts, roles, and role bindings. A role is a set of permissions to access something 
in the Kubernetes API. For example, a `pod-reader` role may have permission to perform the `list`, `get` and `watch` operations on the `pods` resource in a particular namespace, by default the same namespace that the role is configured in. In fact, that's exactly what we are going to configure, as this is the permission that our pods need. Here's the spec for the `pod-reader` role to be added in `kubernetes/akka-cluster.yaml`:

```yaml
---
kind: Role
apiVersion: rbac.authorization.k8s.io/v1
metadata:
  name: pod-reader
rules:
- apiGroups: [""]
  resources: ["pods"]
  verbs: ["get", "watch", "list"]
```

Having configured a role, you can then bind that role to a subject. A subject is typically either a user or a group, and a user may be a human user, 
or it could be a service account. A service account is an account created by Kubernetes for Kubernetes resources, such as applications running in pods, 
to access the Kubernetes API. Each namespace has a default service account that is used by default by pods that don't explicitly declare a 
service account, otherwise, you can define your own service accounts. Kubernetes automatically injects the credentials of a pods service account into 
that pods filesystem, allowing the pod to use them to make authenticated requests on the Kubernetes API.

Since we are just using the default service account, we need to bind our role to the default service account so that our pod will be able to 
access the Kubernetes API as a `pod-reader`. In `kubernetes/akka-cluster.yaml`:

```yaml
---
kind: RoleBinding
apiVersion: rbac.authorization.k8s.io/v1
metadata:
  name: read-pods
subjects:
- kind: User
  name: system:serviceaccount:appka-1:default
roleRef:
  kind: Role
  name: pod-reader
  apiGroup: rbac.authorization.k8s.io
```

Note the service account name, `system:serviceaccount:appka-1:default`, contains the `appka-1` namespace in it. 
You'll need to update it accordingly.

### A note on secrets with RBAC

One thing to be aware of when using role based access control, the `pod-reader` role is going to grant access to read all pods in the 
`appka-1` namespace, not just the pods for your application. This includes the deployment specs, which includes the environment 
variables that are hard coded in the deployment specs. If you pass secrets through those environment variables, rather than using 
the Kubernetes secrets API, then your application, and every other app that uses the default service account, will be able to 
see these secrets. This is a good reason why you should never pass secrets directly in deployment specs, rather, you should 
pass them through the Kubernetes secrets API.

If this is a concern, one solution might be to create a separate namespace for each application you wish to deploy. You may find 
the configuration overhead of doing this very high though, it's not what Kubernetes namespaces are intended to be used for.

## Health Checks

Akka management HTTP includes @ref[health check routes](../healthchecks.md) that will expose liveness and readiness health checks on `/alive` and `/ready` respectively. 

In Kubernetes, if an application is live, it means it is running - it hasn't crashed. But it may not necessarily be ready to serve requests, for example, it might not yet have managed to connect to a database, or, in our case, it may not have formed a cluster yet. 

By separating [liveness and readiness](https://kubernetes.io/docs/concepts/workloads/pods/pod-lifecycle/#container-probes), Kubernetes can distinguish between fatal errors, like crashing, and transient errors, like not being able to contact other resources that the application depends on, allowing Kubernetes to make more intelligent decisions about whether an application needs to be restarted, or if it just needs to be given time to sort itself out.

These routes expose information which is the result of multiple internal checks. For example, by depending on `akka-management-cluster-http` the health checks will take cluster membership status into consideration and will be a check to ensure that a cluster has been formed.

Finally, we need to configure the health checks. As mentioned earlier, Akka Management HTTP provides health check endpoints for us, both for readiness and liveness. Kubernetes just needs to be told about this. The first thing we do is configure a name for the management port, while not strictly necessary, this allows us to refer to it by name in the probes, rather than repeating the port number each time. We'll configure some of the numbers around here, we're going to tell Kubernetes to wait 20 seconds before attempting to probe anything, this gives our cluster a chance to start before Kubernetes starts trying to ask us if it's ready, and since in some scenarios, particularly if you haven't assigned a lot of CPU to your pods, it can take a long time for the cluster to start, so we'll give it a high failure threshold of 10.

Health check probes can be adjusted in `kubernetes/akka-cluster.yaml`:

```yaml
ports:
  - name: management
    containerPort: 8558
readinessProbe:
  httpGet:
    path: "/ready"
    port: management
  periodSeconds: 10
  failureThreshold: 10
  initialDelaySeconds: 20
livenessProbe:
  httpGet:
    path: "/alive"
    port: management
  periodSeconds: 10
  failureThreshold: 10
  initialDelaySeconds: 20
```
