# Akka Management

There are several supported protocols for managing a running Akka Cluster. 
Support for some protocols is provided out of the box and others are provided as separate modules.

@@ toc { .main depth=3 }

@@@ index

  - [Akka Management](akka-management.md)
  - [Akka Cluster Bootstrap](bootstrap/index.md)
  - [Akka Discovery](discovery/index.md)
  - [Akka Cluster Management (HTTP)](cluster-http-management.md)
  - [Akka Cluster Management (JMX)](cluster-jmx-management.md)

@@@


## Compatibility & support

This project does not yet have to obey the rule of staying binary compatible between releases that is common 
for Akka libraries. Breaking API changes may be introduced without notice as we refine and simplify based on your feedback.

Akka gRPC is currently *Incubating*. The Lightbend subscription does not yet cover support for this project.