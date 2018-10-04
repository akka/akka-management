# Akka Management

Akka Management is a suite of tools for operating Akka Clusters.

## Overview

Akka Management consists of multiple modules:

 * @ref[akka-management](akka-management.md) is the base module that provides an extensible HTTP management endpoint for Akka management tools.
 * @ref[akka-discovery](discovery/index.md) is a suite of modules that provides a general service discovery API with implementations based on DNS, kubernetes, consul, and others.
 * @ref[akka-cluster-bootstrap](bootstrap/index.md) helps bootstrapping an Akka cluster using service discovery.
 * @ref[akka-management-cluster-http](cluster-http-management.md) is a module that provides HTTP endpoints for introspecting and managing Akka clusters.
 * @extref[Akka's built-in JMX management support](akka-docs:scala/cluster-usage.html#cluster-jmx) provides JMX MBeans for cluster management.

You don't have to use all the modules but if you do here's how these modules work together:

![project structure](images/structure.png)


@@ toc { .main depth=2 }

@@@ index

  - [Akka Management](akka-management.md)
  - [Akka Cluster Bootstrap](bootstrap/index.md)
  - [Akka Discovery](discovery/index.md)
  - [Akka Cluster Management (HTTP)](cluster-http-management.md)
  - [Akka Cluster Management (JMX)](cluster-jmx-management.md)

@@@
