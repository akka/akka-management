## Kubernetes API

Another discovery implementation provided is one that uses the Kubernetes API. Instead of doing a DNS lookup,
it sends a request to the Kubernetes service API to find the other pods. This method allows you to define health
and readiness checks that don't affect the discovery method. Configuration options are provided to adjust
the namespace, label selector, and port name that are used in the pod selection process.

### Dependencies and usage

Kubernetes currently ignores all fields apart from service name. This is expected to change.

Using `akka-discovery-kubernetes-api` is very simple, as you simply need to depend on the library::

@@dependency[sbt,Gradle,Maven] {
  group="com.lightbend.akka.discovery"
  artifact="akka-discovery-kubernetes-api_2.12"
  version="$version$"
}

And configure it to be used as default discovery implementation in your `application.conf`:

```
akka.discovery {
  method = kubernetes-api
}
```

To find other pods, this method needs to know how they are labeled, what the name of the Akka Management port is, and
what namespace they reside in. Below, you'll find the default configuration. It can be customized by changing these
values in your `application.conf`.

```
akka.discovery {
  kubernetes-api {
    pod-namespace = "default"

    # %s will be replaced with the configured effective name, which defaults to
    # the actor system name
    pod-label-selector = "app=%s"
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
        # When
        # akka.management.cluster.bootstrap.contact-point-discovery.port-name
        # is defined, it must correspond to this name:
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

