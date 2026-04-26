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

### Tuning timeouts

Each lease holder periodically writes to the Kubernetes API server to maintain its lease. The default `heartbeat-interval` of `heartbeat-timeout / 10` (12s with the default 120s timeout) is very conservative. In clusters with many leases — especially when using Cluster Sharding where there is one lease per shard — this can put significant load on the Kubernetes API server. Increasing the `heartbeat-interval` reduces this load.

The lease uses three related timeout settings:

* `heartbeat-interval` — How often the lease holder writes a new timestamp to the lease resource. Default: `heartbeat-timeout / 10`.
* `heartbeat-timeout` — How long a lease can go without being updated before another node may take it over. Default: `120s`.
* `lease-operation-timeout` — The total time allowed for a single lease API operation (acquire, release, or heartbeat update). Default: `5s`.

#### Heartbeat interval to timeout ratio

The ratio between `heartbeat-interval` and `heartbeat-timeout` determines how many heartbeat attempts fit within the timeout window and how quickly a crashed node's lease can be taken over. The default ratio of 1:10 is very conservative. A ratio of 1:5 is a good balance between API server load and safety.

@@@ warning

Do not set the ratio below 1:4. At 1:4 there is only room for approximately one heartbeat retry on transient failures, and below that the retry mechanism provides no benefit. During a retry window the local node continues to believe it holds the lease while writes to the API server are failing. If other nodes can still reach the API server they may observe the lease as expired and acquire it. With the safety margins built in, this overlap is brief and bounded, but reducing the ratio shrinks those margins. This is especially important for Split Brain Resolver, Cluster Singleton, and Cluster Sharding where holding a stale lease can cause split-brain scenarios.

@@@

#### Heartbeat failure retry

When a heartbeat fails due to a transient error (network timeout, API server unavailability), the lease holder does not immediately give up the lease. Instead, it retries the heartbeat as long as there is sufficient time remaining before the `heartbeat-timeout` deadline.

The retry time budget is calculated with safety margins:

* Other nodes consider the lease expired at `heartbeat-timeout - 2 * heartbeat-interval` after the last successful heartbeat.
* The lease holder stops retrying before that, subtracting `lease-operation-timeout` (time for the retry itself) and an additional `heartbeat-interval` (buffer for clock skew between nodes).
* Retries use exponential backoff: starting at `heartbeat-interval / 4`, doubling each attempt (`interval/4`, `interval/2`, `interval`), and capped at `heartbeat-interval`.

Note that version conflicts during heartbeat (meaning another node has modified the lease resource) are *not* retried — they indicate a genuine lease loss.

#### Acquire failure retry

Transient failures during acquire (network timeout, API server unavailability) are retried within the caller's `lease-operation-timeout` budget, spanning both the initial read and the update. A retry is only scheduled while the elapsed time plus the next delay is under half the budget, leaving the rest for the retried call. Backoff starts at `lease-operation-timeout / 20`, doubles each attempt, and is capped at `lease-operation-timeout / 5`.

If an update was applied server-side but the client missed the response, the retry's update is answered with a conflict naming us as owner — this is treated as a successful acquire. Genuine conflicts (a different owner) are not retried.

#### Example configurations

| Configuration | Ratio | Retry deadline | ~Retries on failure |
|---------------|-------|----------------|---------------------|
| interval=12s, timeout=120s, op-timeout=5s (default) | 1:10 | 79s | ~7 |
| interval=24s, timeout=120s, op-timeout=10s | 1:5 | 38s | ~2 |
| interval=30s, timeout=120s, op-timeout=10s | 1:4 | 20s | ~1 |

A recommended configuration for tighter timing:

```
akka.coordination.lease.kubernetes {
  heartbeat-interval = 24s
  heartbeat-timeout = 120s
  lease-operation-timeout = 10s
}
```

### F.A.Q

Q. What happens if the node that holds the lease crashes?

A. Each lease has a Time To Live (TTL) that is set via `akka.coordination.lease.kubernetes.heartbeat-timeout` which defaults to 120s. A lease holder updates the lease periodically to keep the lease (by default every `1/10` of the timeout). If the TTL passes without the lease being updated another node is allowed to take the lease. If a heartbeat fails due to a transient error, the lease holder retries the heartbeat as long as there is sufficient time remaining before the TTL deadline (see @ref:[Tuning timeouts](#tuning-timeouts)). For ultimate safety the heartbeat timeout can be set very high but then an operator would need to come and clear the lease if a lease owner crashes with the lease taken.
   
