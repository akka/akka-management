## Kubernetes API

The typical way to consume a service in Kubernetes is to discover it through DNS: this will take into account liveness/readiness checks, and depending on the configuration take care of load balancing (removing the need for client-side load balancing). For this reason, for general usage the [`akka-dns`](https://doc.akka.io/docs/akka/current/discovery/index.html#discovery-method-dns) implementation is usually a better fit for discovering services in Kubernetes. However, in some cases, such as for @ref[Cluster Bootstrap](../bootstrap/index.md), it is desirable to connect to the pods directly, bypassing any liveness/readiness checks or load balancing. For such situations we provide a discovery implementation that uses the Kubernetes API.

### Dependencies and usage

First, add the dependency on the component:

@@dependency[sbt,Gradle,Maven] {
  group="com.lightbend.akka.discovery"
  artifact="akka-discovery-kubernetes-api_2.12"
  version="$version$"
}

As described above, it is uncommon to use the Kubernetes API discovery
mechanism as your default discovery mechanism. When using it with Akka Cluster
Bootstrap, it is sufficient to configure it as described @ref[here](../bootstrap/kubernetes-api.md).
Otherwise, to load it manually, use `loadServiceDiscovery` on the `Discovery` extension:

Scala
:  @@snip [KubernetesApiServiceDiscoverySpec.scala](/discovery-kubernetes-api/src/test/scala/akka/discovery/kubernetes/KubernetesApiServiceDiscoverySpec.scala) { #kubernetes-api-discovery }

Java
:  @@snip [KubernetesApiDiscoveryDocsTest.java](/discovery-kubernetes-api/src/test/java/docs/KubernetesApiDiscoveryDocsTest.java) { #kubernetes-api-discovery }


To find other pods, this method needs to know how they are labeled, what the name of the target port is, and
what namespace they reside in. Below, you'll find the default configuration. It can be customized by changing these
values in your `application.conf`.

```
akka.discovery {
  kubernetes-api {
    pod-namespace = "default"

    # %s will be replaced with the configured effective name, which defaults to
    # the actor system name
    pod-label-selector = "app=%s"

    # This name must match the ports name in the deployment resource YAML.
    pod-port-name = "management"
  }
}
```

This configuration complements the following Deployment specification:

```
apiVersion: extensions/v1beta1
kind: Deployment
metadata:
  labels:
    app: example
  name: example
spec:
  replicas: 4
  selector:
    matchLabels:
      app: example
  template:
    metadata:
      labels:
        app: example
    spec:
      containers:
      - name: example
        image: example/image:1.0.0
        imagePullPolicy: IfNotPresent
        ports:
        # akka remoting
        - name: remoting
          containerPort: 2552
          protocol: TCP
        # akka-management bootstrap
        # This name must match the name defined in
        # akka.discovery.kubernetes-api.pod-port-name configuration
        - name: management
          containerPort: 8558
          protocol: TCP
```

### Role-Based Access Control

If your Kubernetes cluster has [Role-Based Access Control (RBAC)](https://kubernetes.io/docs/admin/authorization/rbac/)
enabled, you'll also have to grant the Service Account that your pods run under access to list pods. The following
configuration can be used as a starting point. It creates a `Role`, `pod-reader`, which grants access to query pod
information. It then binds the default Service Account to the `Role` by creating a `RoleBinding`.
Adjust as necessary.

> Using Google Kubernetes Engine? Your user will need permission to grant roles. See [Google's Documentation](https://cloud.google.com/kubernetes-engine/docs/how-to/role-based-access-control#prerequisites_for_using_role-based_access_control) for more information.

```yaml
---
#
# Create a role, `pod-reader`, that can list pods and
# bind the default service account in the `default` namespace
# to that role.
#

kind: Role
apiVersion: rbac.authorization.k8s.io/v1
metadata:
  name: pod-reader
rules:
- apiGroups: [""] # "" indicates the core API group
  resources: ["pods"]
  verbs: ["get", "watch", "list"]
---
kind: RoleBinding
apiVersion: rbac.authorization.k8s.io/v1
metadata:
  name: read-pods
subjects:
# Note the `name` line below. The first default refers to the namespace. The second refers to the service account name.
# For instance, `name: system:serviceaccount:myns:default` would refer to the default service account in namespace `myns`
- kind: User
  name: system:serviceaccount:default:default
roleRef:
  kind: Role
  name: pod-reader
  apiGroup: rbac.authorization.k8s.io
```

