<a id="discovery"></a>
# Service Discovery

Akka Discovery provides a simple interface around various ways of locating services, such as DNS
or using configuration or key-value stores like zookeeper, consul etc.

## What is Service Discovery

Akka's Service Discovery talks specifically about discovering hosts and ports that relate to some
logical name of a service.

If you're looking for a way to discover Actors in a Cluster, you may want to look at the Receptionist
pattern from Akka Typed instead. Since it provides a more fine-tuned towards Actors mechanism of
doing the discovery.

## Discovery Method trade-offs

We recommend using the DNS implementation as good default choice, and if an implementation
is available for your specific cloud provider or runtime (such as Kubernetes or Mesos etc),
you can pick those likely gain some additional benefits, read their docs section for details.

## Discovery Method: Akka DNS Discovery

The most natural form of service discovery is to use DNS as the source of truth regarding available 
services. In the simplest version, we query for a service name -- which each cluster manager, such as Kubernetes, Mesos 
or others define using their own naming schemes, and expect to get back a list of IPs that are related to this service.

### Dependencies and usage

Using `akka-discovery-dns` is very simple, as you simply need to depend on the library:

@@dependency[sbt,Gradle,Maven] {
  group="com.lightbend.akka.discovery"
  artifact="akka-discovery-dns_2.12"
  version="$version$"
}

And configure it to be used as default discovery implementation in your `application.conf`:

```
akka.discovery {
  method = akka-dns
}
```

From there on, you can use the generic API that hides the fact which discovery method is being used by calling::

Scala
:   ```
    import akka.discovery.ServiceDiscovery
    val system = ActorSystem("Example")
    // ... 
    val discovery = ServiceDiscovery(system).discovery
    ```

Java
:   ```
    import akka.discovery.ServiceDiscovery;
    
    ActorSystem system = ActorSystem.create("Example");
    // ... 
    SimpleServiceDiscovery discovery = ServiceDiscovery.get(system).discovery();
    ```

### Mechanism explanation

The simplest way of resolving multiple hosts of a (micro-)service is to perform a DNS lookup and treat all returned
`A` records as hosts of the same service cluster. This is how such lookup would look like in Kubernetes (see the 
`bootstrap-joining-demo` demo application if you want to try it out for yourself):

```
$ kubectl exec -it $POD -- dig appka-service.default.svc.cluster.local

; <<>> DiG 9.10.3-P4-Debian <<>> appka-service.default.svc.cluster.local
;; global options: +cmd
;; Got answer:
;; ->>HEADER<<- opcode: QUERY, status: NOERROR, id: 3457
;; flags: qr aa rd ra; QUERY: 1, ANSWER: 4, AUTHORITY: 0, ADDITIONAL: 0

;; QUESTION SECTION:
;appka-service.default.svc.cluster.local. IN A

;; ANSWER SECTION:
appka-service.default.svc.cluster.local. 30 IN A 172.17.0.6
appka-service.default.svc.cluster.local. 30 IN A 172.17.0.2
appka-service.default.svc.cluster.local. 30 IN A 172.17.0.3
appka-service.default.svc.cluster.local. 30 IN A 172.17.0.4

;; Query time: 0 msec
;; SERVER: 10.0.0.10#53(10.0.0.10)
;; WHEN: Fri Dec 08 12:04:38 UTC 2017
;; MSG SIZE  rcvd: 121
```

As you can see, this service consists of 4 nodes, with IPs `172.17.0.2` through `172.17.0.6`.
The "lowest" address (since in this case we assume they all listen on the same management port)

An improved way of DNS discovery are `SRV` records, which are not yet supported by `akka-discovery-dns`,
but would then allow the nodes to also advertise which port they are listening on instead of having to assume a shared 
known port (which in the case of the akka management routes is `19999`).

## Discovery Method: Kubernetes API

Another discovery implementation provided is one that uses the Kubernetes API. Instead of doing a DNS lookup,
it sends a request to the Kubernetes service API to find the other pods. This method allows you to define health
and readiness checks that don't affect the discovery method. Configuration options are provided to adjust
the namespace, label selector, and port name that are used in the pod selection process.

### Dependencies and usage

Using `akka-discovery-kubernetes-api` is very simple, as you simply need to depend on the library::

@@dependency[sbt,Gradle,Maven] {
  group="com.lightbend.akka.discovery"
  artifact="akka-discovery-kubernetes-api"
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

    pod-port-name = "akka-mgmt-http"
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
          containerPort: 2551
          protocol: TCP
        # akka-management bootstrap
        - name: akka-mgmt-http
          containerPort: 8558
          protocol: TCP
```

## Discovery Method: AWS EC2 Discovery

If you're an AWS user, you can use tags to simply mark the instances that belong to the same cluster. Use a tag that
has "service" as the key and set the value to be the name of your service. Note that this implementation is adequate
for users running service clusters on *vanilla* EC2 instances. If you're using Amazon EKS (Amazon Elastic Container 
Service for Kubernetes) or plain Amazon ECS, then this is not the right choice and you may want to use 
the @ref:['Kubernetes API'-based discovery method](discovery.md#discovery-method-kubernetes-api) or some other solution.

Screenshot of two tagged instances:


### Dependencies and usage

This is a separate JAR file:

@@dependency[sbt,Gradle,Maven] {
  group="com.lightbend.akka.discovery"
  artifact="akka-discovery-aws-ec2"
  version="$version$"
}

And in your `application.conf`:

```
akka.discovery {
  method = aws-ec2-tag-based
}
```

Notes:

* Since the implementation uses the Amazon EC2 API, you'll need to make sure that AWS credentials are provided.
The simplest way to do this is to create a IAM role that includes permissions for Amazon EC2 API access.
Attach this IAM role to the instances that make up the cluster. See the docs for
[IAM Roles for Amazon EC2](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/iam-roles-for-amazon-ec2.html).

* In general, for the EC2 instances to "talk to each other" (necessary for forming a cluster), they need to be in the
same security group and [the proper rules have to be set](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/security-group-rules-reference.html#sg-rules-other-instances).

* The implementation, at this time, does not filter based on region or instance type etc.


## How to contribute implementations

Contributions to alternative data-stores or service-discovery APIs built-in to specific cloud systems
are happily accepted. Please open an issue on the github issue tracker to discuss the integration
you'd like to contribute first.

An implementation should keep its configuration in the `akka.discovery`
