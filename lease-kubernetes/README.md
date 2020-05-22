# Kubernetes Lease

## Creating the Custom Resource

A custom resource definition is used to store information for each lease.
[Kubernetes optimistic concurrency control](https://github.com/eBay/Kubernetes/blob/master/docs/devel/api-conventions.md#concurrency-control-and-consistency)
is used to implement a lease that has a single owner at any given time.

To install the Custom Resource admin access is required:

Kubernetes:

```
kubectl apply -f lease.yml
```

Open shift

```
oc apply -f lease.yml
```

Where lease.yml contains:

```yaml
apiVersion: apiextensions.k8s.io/v1beta1
kind: CustomResourceDefinition
metadata:
  name: leases.akka.io
spec:
  group: akka.io
  version: v1
  scope: Namespaced
  names:
    plural: leases
    singular: lease
    kind: Lease
    shortNames:
    - le
```

## Role based access control (RBAC)

Each pod needs permission to read/create and update lease resources. They only need access
for the namespace they in.

An example RBAC that can be used:

```yaml
kind: Role
apiVersion: rbac.authorization.k8s.io/v1
metadata:
  name: lease-access
rules:
  - apiGroups: ["akka.io"]
    resources: ["leases"]
    verbs: ["get", "create", "update"]
---
kind: RoleBinding
apiVersion: rbac.authorization.k8s.io/v1
metadata:
  name: lease-access
subjects:
  - kind: User
    name: system:serviceaccount:<YOUR NAMSPACE>:default
roleRef:
  kind: Role
  name: lease-access
  apiGroup: rbac.authorization.k8s.io
```

This defines a `Role` that is allowed to `get`, `create` and `update` lease objects and a `RoleBinding`
that gives the default service user this role in `<YOUR NAMESPACE>`.

Future versions may also require `delete` access for cleaning up old resources. Current uses within Akka
only create a single lease (`sbr`) so cleanup is not an issue.

## Implementation

### Discovering the K8s API

K8s supports swagger. To enable for minikube start with the following option:

```
minikube start --extra-config=apiserver.enable-swagger-ui=true
```

Then start a proxy locally to the API server:

```
kubectl proxy --port=8080
```

Then visit:

```
http://localhost:8080/swagger-ui/
```


