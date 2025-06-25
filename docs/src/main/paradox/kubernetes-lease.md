# Kubernetes Lease

@@@ warning

This module is marked as @extref:[May Change](akka:common/may-change.html)
The API, configuration and behavior may change based on feedback from initial usage.

@@@

This module is an implementation of an [Akka Coordination Lease](https://doc.akka.io/libraries/akka-core/current/coordination.html#lease) backed 
by a [Custom Resource Definition (CRD)](https://kubernetes.io/docs/concepts/extend-kubernetes/api-extension/custom-resources/) in Kubernetes.
Resources in Kubernetes offer [concurrency control and consistency](https://kubernetes.io/docs/concepts/extend-kubernetes/api-extension/custom-resources/) 
that have been used to build a distributed lease/lock.

A lease can be used for:

* @extref[Split Brain Resolver](akka:split-brain-resolver.html#lease). An additional safety measure so that only one SBR instance can make the decision to remain up.
* @extref:[Cluster Singleton](akka:typed/cluster-singleton.html#lease). A singleton manager can be configured to acquire a lease before creating the singleton.
* @extref:[Cluster Sharding](akka:typed/cluster-sharding.html#lease). Each `Shard` can be configured to acquire a lease before creating entity actors.

In all cases the use of the lease increases the consistency of the feature. However, as the Kubernetes API server 
and its backing `etcd` cluster can also be subject to failure and network issues any use of this lease can reduce availability. 

### Lease Instances

* With @extref[Split Brain Resolver](akka:split-brain-resolver.html#lease) there will be one lease per Akka Cluster
* With multiple Akka Clusters using SBRs in the same namespace, e.g. multiple Lagom 
applications, you must ensure different `ActorSystem` names because they all need a separate lease. 
* With Cluster Sharding and Cluster Singleton there will be more leases 
    - For @extref:[Cluster Singleton](akka:typed/cluster-singleton.html#lease) there will be one per singleton.
    - For @extref:[Cluster Sharding](akka:typed/cluster-sharding.html#lease), there will be one per shard per type.

### Configuring

#### Dependency

@@@note
The Akka dependencies are available from Akka’s secure library repository. To access them you need to use a secure, tokenized URL as specified at https://account.akka.io/token.
@@@

Additionally, add the dependency as below.

@@dependency[sbt,Maven,Gradle] {
  symbol1=AkkaManagementVersion
  value1=$project.version$
  group="com.lightbend.akka.management"
  artifact="akka-lease-kubernetes_$scala.binary.version$"
  version=AkkaManagementVersion
}

#### Creating the Custom Resource Definition for the lease

This requires admin privileges to your Kubernetes / Open Shift cluster but only needs doing once.

Kubernetes:

```
kubectl apply -f lease.yml
```

Where lease.yml contains:

@@snip[lease.yaml](/lease-kubernetes/lease.yml)

#### Role based access control

Each pod needs permission to read/create and update lease resources. They only need access
for the namespace they are in.

An example RBAC that can be used:

```yaml
kind: Role
apiVersion: rbac.authorization.k8s.io/v1
metadata:
  name: lease-access
rules:
  - apiGroups: ["akka.io"]
    resources: ["leases"]
    verbs: ["get", "create", "update", "list"]
---
kind: RoleBinding
apiVersion: rbac.authorization.k8s.io/v1
metadata:
  name: lease-access
subjects:
  - kind: User
    name: system:serviceaccount:<YOUR NAMESPACE>:default
roleRef:
  kind: Role
  name: lease-access
  apiGroup: rbac.authorization.k8s.io
```

This defines a `Role` that is allowed to `get`, `create` and `update` lease objects and a `RoleBinding`
that gives the default service user this role in `<YOUR NAMESPACE>`.

Future versions may also require `delete` access for cleaning up old resources. Current uses within Akka
only create a single lease so cleanup is not an issue.

To avoid giving an application the access to create new leases an empty lease can be created in the same namespace as the application with:

Kubernetes:

```
kubelctl create -f sbr-lease.yml -n <YOUR_NAMESPACE>
```

Where `sbr-lease.yml` contains:

```yml
apiVersion: "akka.io/v1"
kind: Lease
metadata:
  name: <YOUR_ACTORSYSTEM_NAME>-akka-sbr
spec:
  owner: ""
  time: 0

```

@@@ note

The lease gets created only during an actual Split Brain.

@@@

#### Enable in SBR

To enable the lease for use within SBR:

```

akka {
  cluster {
    downing-provider-class = "akka.cluster.sbr.SplitBrainResolverProvider"
    split-brain-resolver {
      active-strategy = "lease-majority"
      lease-majority {
        lease-implementation = "akka.coordination.lease.kubernetes"
      }
    }
  }
}

```

#### Full configuration options

@@snip [reference.conf](/lease-kubernetes/src/main/resources/reference.conf)

### F.A.Q

Q. What happens if the node that holds the lease crashes?

A. Each lease has a Time To Live (TTL) that is set `akka.coordination.lease.kubernetes.heartbeat-timeout` which defaults to 120s. A lease holder updates the lease every `1/10` of the timeout to keep the lease. If the TTL passes without
   the lease being updated another node is allowed to take the lease. For ultimate safety this timeout can be set very high but then an operator would need to come and clear the lease if a lease owner crashes with the lease taken.
   
