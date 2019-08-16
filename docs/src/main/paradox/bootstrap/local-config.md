# Local using config

@extref:[Configuration](akka:/discovery/index.html#discovery-method-configuration) based discovery can be used to see the
Cluster Bootstrap process run locally within an IDE or from the command line.

To use `config` service discovery set the following configuration:

* `akka.management.cluster.bootstrap.contact-point-discovery.discovery-method` to `config`
* `akka.discovery.config.services.[cluster-name]` to the endpoinds of the Akka nodes

For example:

@@snip [application.conf](/integration-test/local/src/main/resources/application.conf) { #discovery }

This configuration will return three endpoints for a service called `local-cluster`.

Akka bootstrap is then configured to lookup `local-cluster` in the `config`:

@@snip [application.conf](/integration-test/local/src/main/resources/application.conf) { #bootstrap }

Three main methods can be run, only overriding the host so the ActorSystem's can all bind to the same port:

@@snip [application.conf](/integration-test/local/src/main/scala/akka/cluster/bootstrap/Main.scala) { #main }

The example uses three loopback addresses: `127.0.0.2-4`. On Mac you'll need to set these up:

```
sudo ifconfig lo0 alias 127.0.0.2 up
sudo ifconfig lo0 alias 127.0.0.3 up
sudo ifconfig lo0 alias 127.0.0.4 up
```

On Linux this should not be required.

Run the three mains: `Node1`, `Node2` and `Node3` and they will form a cluster either in your IDE or from the command line:

```
sbt "integration-test-local/runMain akka.cluster.bootstrap.Node1"
sbt "integration-test-local/runMain akka.cluster.bootstrap.Node2"
sbt "integration-test-local/runMain akka.cluster.bootstrap.Node3"
```

The first time one of the Nodes will form a new cluster and the others will join. Any subsequent restarts
then the node will discover a cluster already exists and join.



