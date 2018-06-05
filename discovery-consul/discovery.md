How to set up Consul based discovery for Akka Cluster
=====================================================

Step 1: Register cluster instances in Consul
--------------------------------------------
Imagine that your app consists of 4 nodes, registered in consul as the following services:
1. `service-a-api` on node `A` with port `1234`
1. `service-a-api` on node `B` with port `1235`
1. `service-a-domain` on node `C` with port `1236`
1. `service-a-domain` on node `B` with port `1237`

Step 2: Expose Akka Management port in those services
-----------------------------------------------------
When Akka Management is started register its binding port as a tag in those services in the following way:
`akka-management-port:19999`


Step 3: Register actor system name in consul for services in cluster
--------------------------------------------------------------------
Add the following tag to Consul entries for the services
`system:cluster-a`

The registered services in consul might look like this now:
1. `service-a-api` on node `A` with port `1234` tags: `akka-management-port:19999`, `system:cluster-a`
1. `service-a-api` on node `B` with port `1235` tags: `akka-management-port:20012`, `system:cluster-a`
1. `service-a-domain` on node `C` with port `1236` tags: `akka-management-port:35012`, `system:cluster-a`
1. `service-a-domain` on node `B` with port `1237` tags: `akka-management-port:24678`, `system:cluster-a`

The important part is that the tag with system name `system:cluster-a` has to be the same for all nodes in cluster and every node should serve Akka Management endpoints under port defined in `akka-management-port:...`. 


Step 4: Start Cluster Bootstrap
-------------------------------
After a moment the cluster should be set up and ready.

This approach does not require creating locks in Consul.
