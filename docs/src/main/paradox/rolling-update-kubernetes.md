# Kubernetes Rolling Update

Akka Rolling Update Kubernetes is a module providing utilities to make rolling updates with Kubernetes as smooth as possible.

Starting from Kubernetes v1.22, ReplicaSets are not scaled down with the youngest node first (see details [here](https://github.com/kubernetes/enhancements/tree/master/keps/sig-apps/2185-random-pod-select-on-replicaset-downscale)). That is because after some time all nodes that were brought up in the same time bucket are treated as equally old and the node to scale down first is chosen randomly.

The oldest node in an Akka cluster has a special role as it hosts singletons. If the oldest node in a cluster changes frequently, singletons need to be moved around as well which can have undesired consequences.

This module provides the Pod Deletion Cost extension which automatically annotates older pods so that they are selected last when removing nodes, providing for better overall stability for the cluster operations.

## Project Info

@@project-info{ projectId="rolling-update-kubernetes" }


## Dependency

Add `akka-rolling-update-kubernetes` to your dependency management tool:

@@dependency[sbt,Gradle,Maven] {
symbol=AkkaManagementVersion
value=$project.version$
group=com.lightbend.akka.management
artifact=akka-rolling-update-kubernetes_$scala.binary.version$
version=AkkaManagementVersion
}


## Using

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


### Configuration

The following configuration is required, more details for each and additional configurations can be found in [reference.conf](https://github.com/akka/akka-management/blob/main/rolling-updates-kubernetes/src/main/resources/reference.conf):

* `akka.rollingupdate.kubernetes.pod-name`: this can be provided easily by setting `KUBERNETES_POD_NAME` environment variable to `metadata.name` on the Kubernetes container spec.

Additionally, the pod annotator needs to know which namespace the pod belongs to. By default, this will be detected by reading the namespace
from the service account secret, in `/var/run/secrets/kubernetes.io/serviceaccount/namespace`, but can be overridden by
setting `akka.rollingupdate.kubernetes.namespace` or by providing `KUBERNETES_NAMESPACE` environment variable.

#### Role based access control

This extension uses the Kubernetes API to set the `pod-deletion-cost` annotation on its own pod. To be able to do that, it requires special permission to be able to `patch` the pod configuration. Each pod only needs access to the namespace they are in.

An example RBAC that can be used:
```yaml
kind: Role
apiVersion: rbac.authorization.k8s.io/v1
metadata:
  name: pod-annotator
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
  name: pod-annotator
  apiGroup: rbac.authorization.k8s.io
```

This defines a `Role` that is allowed to `patch` pod objects and a `RoleBinding`
that gives the default service user this role in `<YOUR NAMESPACE>`.

@@@ note

This RBAC example covers only the permissions needed for this `PodDeletionCost` extension specifically. However, usually you'll also be using @ref:[Kubernetes API](bootstrap/kubernetes-api.md) for discovery and boostrap of your cluster, so you'll need to combine this with any other role required already configured, either by keeping them separately or merging them into a single role.

@@@



