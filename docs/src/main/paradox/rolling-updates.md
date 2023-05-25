# Rolling Updates

Rolling updates allow you to update an application by gradually replacing old nodes with new ones. This ensures that the application remains available throughout the update process, with minimal disruption to clients.

## Graceful shutdown

Akka Cluster can handle hard failures using a downing provider such as Lightbend's @extref:[Split Brain Resolver](akka:split-brain-resolver.html).
However, this should not be relied upon for regular rolling redeploys. Features such as `ClusterSingleton`s and `ClusterSharding`
can safely restart actors on new nodes far quicker when it is certain that a node has shutdown rather than crashed.

Graceful leaving will happen with the default settings as it is part of @extref:[Coordinated Shutdown](akka:actors.html#coordinated-shutdown).
Just ensure that a node is sent a `SIGTERM` and not a `SIGKILL`. Environments such as Kubernetes will do this, it is important to ensure
that if JVM is wrapped with a script that it forwards the signal.

Upon receiving a `SIGTERM` Coordinated Shutdown will:

* Perform a `Cluster(system).leave` on itself
* The status of the member will be changed to Exiting while allowing any shards to be shutdown gracefully and
  `ClusterSingleton`s to be migrated if this was the oldest node. Finally, the node is removed from the Akka Cluster membership.


## Number of nodes to redeploy at once

Akka bootstrap requires a `stable-period` where service discovery returns a stable set of contact points. When doing rolling
updates it is best to wait for a node (or group of nodes) to finish joining the cluster before adding and removing other nodes.

## Cluster Singletons

`ClusterSingleton`s run on the oldest node in the cluster. To avoid singletons moving during every node deployment it is advised
to start a rolling redeploy starting at the newest node. Then `ClusterSingleton`s only move once. Cluster Sharding uses a singleton internally so this is important even if not using singletons directly.


## Kubernetes Rolling Updates

Starting from Kubernetes v1.22, ReplicaSets are not scaled down with the youngest node first (see details [here](https://github.com/kubernetes/enhancements/tree/master/keps/sig-apps/2185-random-pod-select-on-replicaset-downscale)). That is because after some time all nodes that were brought up in the same time bucket are treated as equally old and the node to scale down first is chosen randomly.

As mentioned previously, the oldest node in an Akka cluster has a special role as it hosts singletons. If the oldest node in a cluster changes frequently, singletons need to be moved around as well which can have undesired consequences.

This module provides the Pod Deletion Cost extension which automatically annotates older pods so that they are selected last when removing nodes, providing for better overall stability for the cluster operations.

### Project Info

@@project-info{ projectId="rolling-update-kubernetes" }


### Dependency

Add `akka-rolling-update-kubernetes` to your dependency management tool:

@@dependency[sbt,Gradle,Maven] {
symbol=AkkaManagementVersion
value=$project.version$
group=com.lightbend.akka.management
artifact=akka-rolling-update-kubernetes_$scala.binary.version$
version=AkkaManagementVersion
}


### Using

Akka Pod Deletion Cost extension must be started, this can either be done through config or programmatically.

**Through config**

Listing the `PodDeletionCost` extension among the autoloaded `akka.extensions` in `application.conf` will also cause it to autostart:

```
akka.extensions = ["akka.rollingupdate.kubernetes.PodDeletionCost"]
```

If management or bootstrap configuration is incorrect, the autostart will log an error and terminate the actor system.

**Programmatically**

Scala
:  @@snip [PodDeletionCostCompileOnly.scala](/rolling-update-kubernetes/src/test/scala/doc/akka/rollingupdate/kubernetes/PodDeletionCostCompileOnly.scala) { #start }

Java
:  @@snip [PodDeletionCostCompileOnly.java](/rolling-update-kubernetes/src/test/java/jdoc/akka/rollingupdate/kubernetes/PodDeletionCostCompileOnly.java) { #start }


#### Configuration

The following configuration is required, more details for each and additional configurations can be found in [reference.conf](https://github.com/akka/akka-management/blob/main/rolling-updates-kubernetes/src/main/resources/reference.conf):

* `akka.rollingupdate.kubernetes.pod-name`: this can be provided by setting `KUBERNETES_POD_NAME` environment variable to `metadata.name` on the Kubernetes container spec.

```yaml
        env:
        - name: KUBERNETES_POD_NAME
          valueFrom:
            fieldRef:
              fieldPath: metadata.name
```

Additionally, the pod annotator needs to know which namespace the pod belongs to. By default, this will be detected by reading the namespace
from the service account secret, in `/var/run/secrets/kubernetes.io/serviceaccount/namespace`, but can be overridden by
setting `akka.rollingupdate.kubernetes.namespace` or by providing `KUBERNETES_NAMESPACE` environment variable.

```yaml
        env:
        - name: KUBERNETES_NAMESPACE
          valueFrom:
            fieldRef:
              fieldPath: metadata.namespace
```

#### Role based access control

@@@ warning

This extension uses the Kubernetes API to set the `pod-deletion-cost` annotation on its own pod. To be able to do that, it requires special permission to be able to `patch` the pod configuration. Each pod only needs access to the namespace they are in. If this is a security concern in your environment you may instead use @ref:[Alternative with Custom Resource Definition](#alternative-with-custom-resource-definition).

@@@

An example RBAC that can be used:
```yaml
kind: Role
apiVersion: rbac.authorization.k8s.io/v1
metadata:
  name: pod-patcher
rules:
  - apiGroups: [""] # "" indicates the core API group
    resources: ["pods"]
    verbs: ["patch"] # requires "patch" to annotate the pod
---
kind: RoleBinding
apiVersion: rbac.authorization.k8s.io/v1
metadata:
  name: annotate-pods
subjects:
  - kind: User
    name: system:serviceaccount:<YOUR NAMESPACE>:default
roleRef:
  kind: Role
  name: pod-patcher
  apiGroup: rbac.authorization.k8s.io
```

This defines a `Role` that is allowed to `patch` pod objects and a `RoleBinding`
that gives the default service user this role in `<YOUR NAMESPACE>`.

@@@ note

This RBAC example covers only the permissions needed for this `PodDeletionCost` extension specifically. However, usually you'll also be using @ref:[Kubernetes API](bootstrap/kubernetes-api.md) for discovery and boostrap of your cluster, so you'll need to combine this with any other role required already configured, either by keeping them separately or merging them into a single role.

@@@

#### Alternative with Custom Resource Definition

If it's a security concern in your environment to allow "patch" in RBAC as described above, you can instead use an
intermediate Custom Resource Definition (CRD). Instead of updating the `controller.kubernetes.io/pod-deletion-cost`
annotation directly it will update a `PodCost` custom resource and then you would have an operator that reconciles
that and updates the pod-deletion-cost annotation of the pod resource. 

@@@ note

You would have to write the Kubernetes operator that watches the `PodCost` resource and updates the
`controller.kubernetes.io/pod-deletion-cost` annotation of the corresponding pod resource. This operator
is not provided by Akka.

@@@

Enable updates of custom resource with configuration:

```
akka.rollingupdate.kubernetes.custom-resource.enabled = true
```

The `PodCost` CRD:

@@snip [pod-cost.yml](/rolling-update-kubernetes/pod-cost.yml) {}

The RBAC for the application to update the `PodCost` CR, instead of "patch" of the "pods" resources:

```
kind: Role
apiVersion: rbac.authorization.k8s.io/v1
metadata:
  name: podcost-access
rules:
  - apiGroups: ["akka.io"]
    resources: ["podcosts"]
    verbs: ["get", "create", "update", "delete", "list"]
---
kind: RoleBinding
apiVersion: rbac.authorization.k8s.io/v1
metadata:
  name: podcost-access
subjects:
  - kind: User
    name: system:serviceaccount:<YOUR NAMESPACE>:default
roleRef:
  kind: Role
  name: podcost-access
  apiGroup: rbac.authorization.k8s.io
```

## app-version from Deployment

When using Cluster Sharding, it is [recommended](https://doc.akka.io/docs/akka/current/additional/rolling-updates.html#cluster-sharding) to define an increasing `akka.cluster.app-version` configuration property for each roll out. 

This works well unless you use `kubectl rollout undo` which deploys the previous ReplicaSet configuration which contains the previous value for that config. 

To fix this, you can use `AppVersionRevision` to read the current annotation `deployment.kubernetes.io/revision` (part of the ReplicaSet) from the Kubernetes Deployment via the Kubernetes api which always increases, also during a rollback.

### Using

The AppVersionRevision extension must be started, this can either be done through config or programmatically.

**Through config**

Listing the `AppVersionRevision` extension among the autoloaded `akka.extensions` in `application.conf` will also cause it to autostart:

```
akka.extensions = ["akka.rollingupdate.kubernetes.AppVersionRevision"]
```

If the extension configuration is incorrect, the autostart will log an error and terminate the actor system.

**Programmatically**


Scala
:  @@snip [AppVersionRevisionCompileOnly.scala](/rolling-update-kubernetes/src/test/scala/doc/akka/rollingupdate/kubernetes/AppVersionRevisionCompileOnly.scala) { #start }

Java
:  @@snip [AppVersionRevisionCompileOnly.java](/rolling-update-kubernetes/src/test/java/jdoc/akka/rollingupdate/kubernetes/AppVersionRevisionCompileOnly.java) { #start }

#### Configuration

The following configuration is required, more details for each and additional configurations can be found in [reference.conf](https://github.com/akka/akka-management/blob/main/rolling-updates-kubernetes/src/main/resources/reference.conf):

* `akka.rollingupdate.kubernetes.pod-name`: this can be provided by setting `KUBERNETES_POD_NAME` environment variable to `metadata.name` on the Kubernetes container spec.

```yaml
        env:
        - name: KUBERNETES_POD_NAME
          valueFrom:
            fieldRef:
              fieldPath: metadata.name
```

Additionally, the pod annotator needs to know which namespace the pod belongs to. By default, this will be detected by reading the namespace
from the service account secret, in `/var/run/secrets/kubernetes.io/serviceaccount/namespace`, but can be overridden by
setting `akka.rollingupdate.kubernetes.namespace` or by providing `KUBERNETES_NAMESPACE` environment variable.

```yaml
        env:
        - name: KUBERNETES_NAMESPACE
          valueFrom:
            fieldRef:
              fieldPath: metadata.namespace
```

#### Role based access control

Make sure to provide access to corresponding rbac rules `apiGroups` and `resources` like this:

```
kind: Role
apiVersion: rbac.authorization.k8s.io/v1
metadata:
  name: pod-reader
rules:
- apiGroups: [""]
  resources: ["pods"]
  verbs: ["get", "list"]
- apiGroups: ["apps"]
  resources: ["replicasets"]
  verbs: ["get", "list"]
---
kind: RoleBinding
apiVersion: rbac.authorization.k8s.io/v1
metadata:
  name: pod-reader
subjects: 
- kind: ServiceAccount
  name: default
roleRef:
  kind: Role
  name: pod-reader
  apiGroup: rbac.authorization.k8s.io
```
