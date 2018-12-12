# Akka Management

Akka Management is a suite of tools for operating Akka Clusters.
The current version depends on Akka `2.5.19+`, for older versions of Akka use version `0.20.0`

## Overview

Akka Management consists of multiple modules:

 * @ref[akka-management](akka-management.md) is the base module that provides an extensible HTTP management endpoint for Akka management tools.
 * @ref[akka-cluster-bootstrap](bootstrap/index.md) helps bootstrapping an Akka cluster using [Akka Discovery](https://doc.akka.io/docs/akka/current/discovery/index.html).
 * @ref[akka-management-cluster-http](cluster-http-management.md) is a module that provides HTTP endpoints for introspecting and managing Akka clusters.
 * @extref[Akka's built-in JMX management support](akka-docs:scala/cluster-usage.html#cluster-jmx) provides JMX MBeans for cluster management.
 
 As well as [Akka Discovery](https://doc.akka.io/docs/akka/current/discovery/index.html) methods for:
 
 * @ref[Kubernetes API](discovery/kubernetes.md)
 * @ref[Consul](discovery/consul.md)
 * @ref[Marathon API](discovery/marathon.md)
 * @ref[AWS](discovery/aws.md)

You don't have to use all the modules but if you do here's how these modules work together:

![project structure](images/structure.png)


@@@ index

  - [Akka Management](akka-management.md)
  - [Akka Cluster Bootstrap](bootstrap/index.md)
  - [Akka Discovery Methods](discovery/index.md)
  - [Akka Cluster Management (HTTP)](cluster-http-management.md)
  - [Akka Cluster Management (JMX)](cluster-jmx-management.md)

@@@


## Compatibility & support

This project does not yet have to obey the rule of staying binary compatible between releases that is common 
for Akka libraries. Breaking API changes may be introduced without notice as we refine and simplify based on your feedback.

Akka Management is currently *Incubating*. The Lightbend subscription does not yet cover support for this project.