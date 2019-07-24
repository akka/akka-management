## Kubernetes API

The typical way to consume a service in Kubernetes is to discover it through DNS: this will take into account liveness/readiness checks, and depending on the configuration take care of load balancing (removing the need for client-side load balancing). For this reason, for general usage the @extref:[`akka-dns`](akka:discovery/index.html#discovery-method-dns) implementation is usually a better fit for discovering services in Kubernetes. However, in some cases, such as for @ref[Cluster Bootstrap](../bootstrap/index.md), it is desirable to connect to the pods directly, bypassing any liveness/readiness checks or load balancing. For such situations we provide a discovery implementation that uses the Kubernetes API.

## Project Info

@@project-info{ projectId="akka-discovery-kubernetes-api" }

### Dependencies and usage

First, add the dependency on the component:

@@dependency[sbt,Gradle,Maven] {
  group="com.lightbend.akka.discovery"
  artifact="akka-discovery-kubernetes-api_2.12"
  version="$project.version$"
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
    # Namespace discovery path
    #
    # If this path doesn't exist, the namespace will default to "default".
    pod-namespace-path = "/var/run/secrets/kubernetes.io/serviceaccount/namespace"
  
    # Namespace to query for pods.
    #
    # Set this value to a specific string to override discovering the namespace using pod-namespace-path.
    pod-namespace = "<pod-namespace>"

    # Selector value to query pod API with.
    # `%s` will be replaced with the configured effective name, which defaults to the actor system name
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

@@snip [akka-cluster.yml](/integration-test/kubernetes-api/kubernetes/akka-cluster.yml) { #rbac }
