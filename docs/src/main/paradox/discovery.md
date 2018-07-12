<a id="discovery"></a>
# Service Discovery

Akka Discovery provides a simple interface around various ways of locating services, such as DNS
or using configuration or key-value stores like zookeeper, consul etc.

## What is Service Discovery

Akka's Service Discovery talks specifically about discovering hosts and ports that relate to some
logical name of a service.

If you're looking for a way to discover Actors in a Cluster, you may want to look at the [Receptionist
pattern](https://doc.akka.io/docs/akka/current/typed/actor-discovery.html#receptionist) from Akka 
Typed instead. Since it provides a more fine-tuned towards Actors mechanism of doing the discovery.

Discovery is done in a mechanism agnostic way, where mechanisms are things like DNS, Consul and configuration.

Scala
:   ```scala
    import akka.discovery.ServiceDiscovery
    val system = ActorSystem("Example")
    // ... 
    val discovery = ServiceDiscovery(system).discovery
    val result: Future[Resolved] = discovery.lookup("service-name", resolveTimeout = 500 milliseconds)
    ```

Java
:   ```java
    import akka.discovery.ServiceDiscovery; 
    ActorSystem system = ActorSystem.create("Example");
    // ... 
    SimpleServiceDiscovery discovery = ServiceDiscovery.get(system).discovery();
    Future<SimpleServiceDiscovery.Resolved> result = discovery.lookup("service-name", Duration.create("500 millis"));
 
 
When doing a lookup the following parameters are optional: 
* service 
* port
* protocol 

Each discovery mechanism chooses if and how to use them, most currently do not.

## Discovery Method trade-offs

We recommend using the DNS implementation as good default choice, and if an implementation
is available for your specific cloud provider or runtime (such as Kubernetes or Mesos etc),
you can pick those likely gain some additional benefits, read their docs section for details.

## Discovery Method: DNS


### Dependencies and usage

DNS discovery maps `Lookup` queries as follows:
* Simple: A DNS query for A/AAAA records
* Full(name, port, protocol): As a SRV query in the form: `_port._protocol._name` Where the `_`s are added. 

The mapping between Akka service discovery terminology and SRV terminology:
* SRV service = port
* SRV name = name
* SRV protocol = protocol

To use `akka-discovery-dns` depend on the library:

@@dependency[sbt,Gradle,Maven] {
  group="com.lightbend.akka.discovery"
  artifact="akka-discovery-dns_2.12"
  version="$version$"
}

And configure it to be used as discovery implementation in your `application.conf` and `async-dns` to be uses
as the Akka DNS resolver:

```
akka {
  discovery.method = akka-dns
  io.dns.resolver = async-dns
}
```

From there on, you can use the generic API that hides the fact which discovery method is being used by calling::

Scala
:   ```scala
    import akka.discovery.ServiceDiscovery
    val system = ActorSystem("Example")
    // ... 
    val discovery = ServiceDiscovery(system).discovery
    val result: Future[Resolved] = discovery.lookup("service-name", resolveTimeout = 500 milliseconds)
    ```

Java
:   ```java
    import akka.discovery.ServiceDiscovery; 
    ActorSystem system = ActorSystem.create("Example");
    // ... 
    SimpleServiceDiscovery discovery = ServiceDiscovery.get(system).discovery();
    Future<SimpleServiceDiscovery.Resolved> result = discovery.lookup("service-name", Duration.create("500 millis"));
    ```

### How it works

DNS discovery will use either A/AAAA records or SRV records depending on whether a `Simple` or `Full` lookup is issued.. 
The advantage of SRV records is that they can include a port.
Container schedulers such as [Kubernetes support both](https://kubernetes.io/docs/concepts/services-networking/dns-pod-service/).


#### SRV records


`Full` lookups become SRV queries. For example:

```
dig srv service.tcp.akka.test                                                                                                                                                                                                                                                                                                                      

; <<>> DiG 9.11.3-RedHat-9.11.3-6.fc28 <<>> srv service.tcp.akka.test
;; global options: +cmd
;; Got answer:
;; ->>HEADER<<- opcode: QUERY, status: NOERROR, id: 60023
;; flags: qr aa rd ra; QUERY: 1, ANSWER: 2, AUTHORITY: 1, ADDITIONAL: 5

;; OPT PSEUDOSECTION:
; EDNS: version: 0, flags:; udp: 4096
; COOKIE: 5ab8dd4622e632f6190f54de5b28bb8fb1b930a5333c3862 (good)
;; QUESTION SECTION:
;service.tcp.akka.test.         IN      SRV

;; ANSWER SECTION:
service.tcp.akka.test.  86400   IN      SRV     10 60 5060 a-single.akka.test.
service.tcp.akka.test.  86400   IN      SRV     10 40 5070 a-double.akka.test.

```

In this case `service.tcp.akka.test` resolves to `a-single.akka.test` on port `5060`
and `a-double.akka.test` on port `5070`. Currently discovery does not support the weightings.

#### A/AAAA records

`Simple` lookups become A/AAAA record queries. For example:

```
dig a-double.akka.test

; <<>> DiG 9.11.3-RedHat-9.11.3-6.fc28 <<>> a-double.akka.test
;; global options: +cmd
;; Got answer:
;; ->>HEADER<<- opcode: QUERY, status: NOERROR, id: 11983
;; flags: qr aa rd ra; QUERY: 1, ANSWER: 2, AUTHORITY: 1, ADDITIONAL: 2

;; OPT PSEUDOSECTION:
; EDNS: version: 0, flags:; udp: 4096
; COOKIE: 16e9815d9ca2514d2f3879265b28bad05ff7b4a82721edd0 (good)
;; QUESTION SECTION:
;a-double.akka.test.            IN      A

;; ANSWER SECTION:
a-double.akka.test.     86400   IN      A       192.168.1.21
a-double.akka.test.     86400   IN      A       192.168.1.22

```

In this case `a-double.akka.test` would resolve to `192.168.1.21` and `192.168.1.22`.

## Discovery Method: Configuration

Configuration currently ignores all fields on a Full lookup apart from name. 

For simple use cases configuration can be used for service discovery. The advantage of using Akka Discovery with
configuration rather than your own configuration values is that applications can be migrated to a more 
sophisticated discovery mechanism without any code changes. 

To use configuration discovery add it as a dependency:

@@dependency[sbt,Gradle,Maven] {
  group="com.lightbend.akka.discovery"
  artifact="akka-discovery-config_2.12"
  version="$version$"
}

And configure it to be used as discovery implementation in your `application.conf` 

```
akka {
  discovery.method = config
}
```

By default the services discoverable are defined in `akka.discovery.config.services` and have the following format:

```
akka.discovery.config.services = {
  service1 = {
    endpoints = [
      {
        host = "cat"
        port = 1233
      },
      {
        host = "dog"
        port = 1234
      }
    ]
  },
  service2 = {
    endpoints = []
  }
}
```

Where the above block defines two services, `service1` and `service2`.
Each service can have multiple endpoints.


## Discovery Method: Kubernetes API

Another discovery implementation provided is one that uses the Kubernetes API. Instead of doing a DNS lookup,
it sends a request to the Kubernetes service API to find the other pods. This method allows you to define health
and readiness checks that don't affect the discovery method. Configuration options are provided to adjust
the namespace, label selector, and port name that are used in the pod selection process.

### Dependencies and usage

Kubernetes currently ignores all fields on a Full lookup apart from name. This is expected to change.

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
          containerPort: 2552
          protocol: TCP
        # akka-management bootstrap
        - name: akka-mgmt-http
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

## Discovery Method: Marathon API

Marathon currently ignores all fields on a Full lookup apart from name. This is expected to change.

If you're a Mesos or DC/OS user, you can use the provided Marathon API implementation. You'll need to add a label
to your Marathon JSON (named `ACTOR_SYSTEM_NAME`  by default) and set the value equal to the name of the configured
effective name, which defaults to your applications actor system name.

You'll also have to add a named port, by default `akkamgmthttp`, and ensure that Akka Management's HTTP interface
is bound to this port.

### Dependencies and usage

This is a separate JAR file:

@@dependency[sbt,Gradle,Maven] {
  group="com.lightbend.akka.discovery"
  artifact="akka-discovery-marathon-api_2.12"
  version="$version$"
}

And in your `application.conf`:

```
akka.discovery {
  method = marathon-api
}
```

And in your `marathon.json`:
```
{
   ...
   "cmd": "path-to-your-app -Dakka.remote.netty.tcp.hostname=$HOST -Dakka.remote.netty.tcp.port=$PORT_AKKAREMOTE -Dakka.management.http.hostname=$HOST -Dakka.management.http.port=$PORT_AKKAMGMTHTTP",

   "labels": {
     "ACTOR_SYSTEM_NAME": "my-system"
   },

   "portDefinitions": [
     { "port": 0, "name": "akkaremote" },
     { "port": 0, "name": "akkamgmthttp" }
   ]
   ...
}
```

## Discovery Method: AWS API

If you're using EC2 directly _or_ you're using ECS with host mode networking
_and_ you're deploying one container per cluster member, continue to
@ref:[Discovery Method: AWS API - EC2 Tag-Based Discovery](discovery.md#discovery-method-aws-api-ec2-tag-based-discovery).

If you're using ECS with
[awsvpcs](https://aws.amazon.com/blogs/compute/introducing-cloud-native-networking-for-ecs-containers/)
mode networking (whether on EC2 or with
[Fargate](https://aws.amazon.com/fargate/)), continue to
@ref:[Discovery Method: AWS API - ECS Discovery](discovery.md#discovery-method-aws-api-ecs-discovery).

ECS with bridge mode networking is not supported.

If you're using EKS, then you may want to use the
@ref:['Kubernetes API'-based discovery method](discovery.md#discovery-method-kubernetes-api)
instead.


### Discovery Method: AWS API - EC2 Tag-Based Discovery

You can use tags to simply mark the instances that belong to the same cluster. Use a tag that
has "service" as the key and set the value equal to the name of your service (same value as `akka.cluster.bootstrap.contact-point-discovery.service-name` 
defined in `application.conf`, if you're using this module for bootstrapping your Akka cluster).
 
Screenshot of two tagged EC2 instances:

![EC2 instances](images/discovery-aws-ec2-tagged-instances.png)

Note the tag **service** -> *products-api*. It is set on both instances. 
 
Note that this implementation is adequate for users running service clusters on *vanilla* EC2 instances. These
instances can be created and tagged manually, or created via an auto-scaling group (ASG). If they are created via an ASG,
they can be tagged automatically on creation. Simply add the tag to the auto-scaling group configuration and 
ensure the "Tag New Instances" option is checked.


#### Dependencies and usage

This is a separate JAR file:

@@dependency[sbt,Gradle,Maven] {
  group="com.lightbend.akka.discovery"
  artifact="akka-discovery-aws-api_2.12"
  version="$version$"
}

And in your `application.conf`:

```
akka.discovery {
  method = aws-api-ec2-tag-based
}
```

Notes:

* Since the implementation uses the Amazon EC2 API, you'll need to make sure that AWS credentials are provided.
The simplest way to do this is to create an IAM role that includes permissions for Amazon EC2 API access.
Attach this IAM role to the instances that make up the cluster. See the docs for
[IAM Roles for Amazon EC2](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/iam-roles-for-amazon-ec2.html).

* In general, for the EC2 instances to "talk to each other" (necessary for forming a cluster), they need to be in the
same security group and [the proper rules have to be set](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/security-group-rules-reference.html#sg-rules-other-instances).

* You can set additional filters (by instance type, region, other tags etc.) in your application.conf file, in the
`akka.discovery.aws-api-ec2-tag-based.filters` key. The filters have to be key=value pairs separated by the semicolon
character. For example: 
    ```
    akka {
      discovery {
        aws-api-ec2-tag-based {
          filters = "instance-type=m1.small;tag:purpose=production"
        }
      }
    }
    ```

* This module does not support running multiple Akka nodes (i.e. multiple JVMs) per EC2 instance. 

* You can change the default tag key from "service" to something else. This can be done via `application.conf`, by 
setting `akka.discovery.aws-api-ec2-tag-based.tag-key` to something else. 
    ```
    akka.discovery.aws-api-ec2-tag-based.tag-key = "akka-cluster"
    ```

Demo:

* A working demo app is available in the [bootstrap-joining-demo](https://github.com/akka/akka-management/tree/master/bootstrap-joining-demo/aws-api-ec2) 
folder.


### Discovery Method: AWS API - ECS Discovery

If you're using ECS with
[awsvpc](https://aws.amazon.com/blogs/compute/introducing-cloud-native-networking-for-ecs-containers/)
mode networking, you can have all task instances of a given ECS service discover
each other. If you're using this module for bootstrapping your Akka cluster that
you'll do so by setting the value of
`akka.cluster.bootstrap.contact-point-discovery.service-name` to that of the
ECS service itself.
 
Screenshot of two ECS task instances (the service name is
`liquidity-application`):

![ECS task instances](images/discovery-aws-ecs-task-instances.png)


#### Dependencies and usage

There are two "flavours" of the ECS Discovery module. Functionally they are
identical; the difference is in which version of the AWS SDK they use. They are
both provided so that you can choose which set of AWS SDK dependencies you're
most comfortable with bringing in to your project.

##### akka-discovery-aws-api

This uses the mainstream AWS SDK. The advantage here is that if you've already
got the mainstream AWS SDK as a dependency you're not now also bringing in the
preview SDK. The disadvantage is that the mainstream SDK does blocking IO.

@@dependency[sbt,Gradle,Maven] {
  group="com.lightbend.akka.discovery"
  artifact="akka-discovery-aws-api_2.12"
  version="$version$"
}

And in your `application.conf`:

```
akka.discovery {
  method = aws-api-ecs
  aws-api-ecs {
    # Defaults to "default" to match the AWS default cluster name if not overridden
    cluster = "your-ecs-cluster-name"
  }
}
```


##### akka-discovery-aws-api-async

This uses the preview AWS SDK. The advantage here is that the SDK does
non-blocking IO, which you probably want. You might need to think carefully
before using this though if you've already got the mainstream AWS SDK as a
dependency.

Once the async AWS SDK is out of preview it is likely that the
`akka-discovery-aws-api` module will be discontinued in favour of
`akka-discovery-aws-api-async`.

@@dependency[sbt,Gradle,Maven] {
  group="com.lightbend.akka.discovery"
  artifact="akka-discovery-aws-api-async_2.12"
  version="$version$"
}

And in your `application.conf`:

```
akka.discovery {
  method = aws-api-ecs-async
  aws-api-ecs-async {
    # Defaults to "default" to match the AWS default cluster name if not overridden
    cluster = "your-ecs-cluster-name"
  }
}
```


Notes:

* Since the implementation uses the AWS ECS API, you'll need to make sure that
  AWS credentials are provided. The simplest way to do this is to create an IAM
  role that includes appropriate permissions for AWS ECS API access. Attach
  this IAM role to the task definition of the ECS Service. See the docs for
  [IAM Roles for Tasks](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/task-iam-roles.html).

* In general, for the ECS task instances to "talk to each other" (necessary for
  forming a cluster), they need to be in the same security group and the proper
  rules have to be set. See the docs for
  [Task Networking with the `awsvpc` Network Mode](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/task-networking.html).

* akka-remote by default sets `akka.remote.netty.tcp.hostname` to the result of
  `InetAddress.getLocalHost.getHostAddress`, and akka-management does the same
  for `akka.management.http.hostname`. However,
  `InetAddress.getLocalHost.getHostAddress` throws an exception when running in
  awsvpc mode (because the container name cannot be resolved), so you will need
  to set this explicitly. An alternative host address discovery method is
  provided by both modules. The methods are
  `EcsSimpleServiceDiscovery.getContainerAddress` and
  `AsyncEcsSimpleServiceDiscovery.getContainerAddress` respectively, which you
  should use to programmatically set both config hostnames.

* Because ECS service discovery is only able to discover IP addresses (not ports
  too) you'll need to set
  `akka.management.cluster.bootstrap.contact-point.fallback-port = 8558`, where
  8558 is whatever port you choose to bind akka-management to.

* The current implementation only supports discovery of service task instances
  within the same region.

Demo:

* A working demo app is available in the
  [bootstrap-joining-demo](https://github.com/akka/akka-management/tree/master/bootstrap-joining-demo/aws-api-ecs) 
  folder. It includes CloudFormation templates with minimal permissions w.r.t to
  IAM policies and security group ingress, and so is a good starting point for
  any deployment that integrates the
  [principle of least privilege](https://en.wikipedia.org/wiki/Principle_of_least_privilege). 


## Discovery Method: Consul

Consul currently ignores all fields on the Full lookup apart from name. This is expected to change.

If you are using Consul to do the service discovery this would allow you to base your Cluster on Consul services.

@@dependency[sbt,Gradle,Maven] {
  group="com.lightbend.akka.discovery"
  artifact="akka-discovery-consul_2.12"
  version="$version$"
}

@@@ note

This discovery implementation has been recently contributed and we do not fully "support" it yet.
However it should work fine and has been used in production by the person contributing it.

@@@

In your application conf add:
```
akka.discovery {
  method = akka-consul
  akka-consul {
        
    #How to connect to Consul to fetch services data   
    consul-host = "127.0.0.1"
    consul-port = 8500
   
    # Prefix for consul tag with the name of the actor system / application name,
    # services with this tag present will be found by the discovery mechanism
    # i.e. `system:test` will be found in cluster if the cluster system is named `test`
    application-name-tag-prefix = "system:"
    
    # Prefix for tag containing port number where akka management is set up so that
    # the seed nodes can be found, an example value for the tag would be `akka-management-port:19999`
    application-akka-management-port-tag-prefix = "akka-management-port:"
  }
}
```

Notes:

* Since tags in Consul services are simple strings, prefixes are necessary to ensure that proper values are read.

* If Akka management port tag is not found on service in Consul the implementation defaults to catalog service port.

## Discovery Method: Aggregate multiple discovery mechanisms

Aggregate discovery allows multiple discovery mechanisms to be aggregated e.g. try and resolve
via DNS and fall back to configuration.

To use aggregate discovery add its dependency as well as all of the discovery mechanisms that you 
want to aggregate.

@@dependency[sbt,Gradle,Maven] {
  group="com.lightbend.akka.discovery"
  artifact="akka-discovery-aggregate_2.12"
  version="$version$"
}

Configure `aggregate` as `akka.discovery.method` and which discovery mechanisms are tried and in which order.

```
akka {
  discovery {
    method = aggregate
    aggregate {
      discovery-mechanisms = ["akka-dns", "config"]
    }
    config {
      services {
        service1 {
          endpoints [
            {
              host = "host1"
              port = 1233
            },
            {
              host = "host2"
              port = 1234
            }
          ]
        }
      }
    }
  }
}

```

The above configuration will result in `akka-dns` first being checked and if it fails or returns no
targets for the given service name then `config` is queried which i configured with one service called
`service1` which two hosts `host1` and `host2`.

## How to contribute implementations

Contributions to alternative data-stores or service-discovery APIs built-in to specific cloud systems
are happily accepted. Please open an issue on the github issue tracker to discuss the integration
you'd like to contribute first.

An implementation should keep its configuration under `akka.discovery`.
