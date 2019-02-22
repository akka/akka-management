<a id="http-cluster-management"></a>
# Cluster Http Management

Akka Management Cluster HTTP is a Management extension that allows you interaction with an `akka-cluster` through an HTTP interface.
This management extension exposes different operations to manage nodes in a cluster as well as a health checks based
on the cluster state.

The operations exposed are comparable to the Command Line Management tool or the JMX interface `akka-cluster` provides.

## Dependencies

The Akka Cluster HTTP Management is a separate jar file.
Make sure to include it along with the core akka-management library in your project:

@@dependency[sbt,Gradle,Maven] {
  group=com.lightbend.akka.management
  artifact=akka-management_$scala.binary_version$
  version=$version$
  group2=com.lightbend.akka.management
  artifact2=akka-management-cluster-http_$scala.binary.version$
  version2=$version$
}

## Running

To make sure the Akka Cluster HTTP Management is running, register it with Akka Management:

Scala
:  @@snip [CompileOnlySpec.scala](/cluster-http/src/test/scala/doc/akka/cluster/http/management/CompileOnlySpec.scala) { #loading }

Java
:  @@snip [CompileOnlyTest.java](/cluster-http/src/test/java/jdoc/akka/cluster/http/management/CompileOnlyTest.java) { #loading }

## API Definition

The following table describes the usage of the API. All `GET` operations are exposed by default. `POST`, `PUT` and `DELETE` operations
are only enabled if `akka.management.http.route-providers-read-only` is set to `false`.

| Path                         | HTTP method | Required form fields | Description
| ---------------------------- | ----------- | -------------------- | -----------
| `/cluster/members/`          | GET         | None                 | Returns the status of the Cluster in JSON format.
| `/cluster/members/`          | POST        | address: `{address}` | Executes join operation in cluster for the provided `{address}`.
| `/cluster/members/{address}` | GET         | None                 | Returns the status of `{address}` in the Cluster in JSON format.
| `/cluster/members/{address}` | DELETE      | None                 | Executes leave operation in cluster for provided `{address}`.
| `/cluster/members/{address}` | PUT         | operation: Down      | Executes down operation in cluster for provided `{address}`.
| `/cluster/members/{address}` | PUT         | operation: Leave     | Executes leave operation in cluster for provided `{address}`.
| `/cluster/shards/{name}`     | GET         | None                 | Returns shard info for the shard region with the provided `{name}`

The expected format of `address` follows the Cluster URI convention. Example: `akka://Main@myhostname.com:3311`

In the paths `address` is also allowed to be provided without the protocol prefix. Example: `Main@myhostname.com:3311`

### Get /cluster/members responses

| Response code | Description
| ------------- | -----------
| 200           | Status of cluster in JSON format
| 500           | Something went wrong. Cluster might be shutdown.

 Example response:

     {
       "selfNode": "akka.tcp://test@10.10.10.10:1111",
       "members": [
         {
           "node": "akka.tcp://test@10.10.10.10:1111",
           "nodeUid": "1116964444",
           "status": "Up",
           "roles": []
         }
       ],
       "unreachable": [],
       "leader: "akka.tcp://test@10.10.10.10:1111",
       "oldest: "akka.tcp://test@10.10.10.10:1111"
     }

Where `oldest` is the oldest node in the current datacenter.

### Post /cluster/members responses

| Response code | Description
| ------------- | -----------
| 200           | Executing join operation.
| 500           | Something went wrong. Cluster might be shutdown.

Example response:

    Joining akka.tcp://test@10.10.10.10:111

### Get /cluster/members/{address} responses

| Response code | Description
| ------------- | -----------
| 200           | Status of cluster in JSON format
| 404           | No member was found in the cluster for the given `{address}`.
| 500           | Something went wrong. Cluster might be shutdown.

Example response:

    {
      "node": "akka.tcp://test@10.10.10.10:1111",
      "nodeUid": "-169203556",
      "status": "Up",
      "roles": []
    }

### Delete /cluster/members/{address} responses

| Response code | Description
| ------------- | -----------
| 200           | Executing leave operation.
| 404           | No member was found in the cluster for the given `{address}`.
| 500           | Something went wrong. Cluster might be shutdown.

Example response:

    Leaving akka.tcp://test@10.10.10.10:111

### Put /cluster/members/{address} responses

| Response code | Operation | Description
| ------------- | --------- | -----------
| 200           | Down      | Executing down operation.
| 200           | Leave     | Executing leave operation.
| 400           |           | Operation supplied in `operation` form field is not supported.
| 404           |           | No member was found in the cluster for the given `{address}`
| 500           |           | Something went wrong. Cluster might be shutdown.

Example response:

    Downing akka.tcp://test@10.10.10.10:111

### Get /cluster/shards/{name} responses

| Response code | Description
| ------------- | -----------
| 200           | Shard region information in JSON format
| 404           | No shard region was found on the node for the given `{name}`

 Example response:

     {
       "regions": [
         {
           "shardId": "1234",
           "numEntities": 30
         }
       ]
     }

## Hosting the routes in an existing Akka HTTP server

Starting `AkkaMangement` starts an Akka HTTP server and hosts the Cluster HTTP Routes. The routes can also be added
to an existing Akka HTTP server. To access all the routes:

Scala
:  @@snip [CompileOnlySpec.scala](/cluster-http/src/test/scala/doc/akka/cluster/http/management/CompileOnlySpec.scala) { #all }

Java
:  @@snip [CompileOnlyTest.java](/cluster-http/src/test/java/jdoc/akka/cluster/http/management/CompileOnlyTest.java) { #all }

Just the read only routes can be accessed:

Scala
:  @@snip [CompileOnlySpec.scala](/cluster-http/src/test/scala/doc/akka/cluster/http/management/CompileOnlySpec.scala) { #read-only }

Java
:  @@snip [CompileOnlyTest.java](/cluster-http/src/test/java/jdoc/akka/cluster/http/management/CompileOnlyTest.java) { #read-only }

## Disable routes

The Cluster HTTP Routes are included by default when this module is used. It can be disabled with the following
configuration, for example if the cluster membership health checks are to be included but not the other Cluster HTTP Routes.

```
akka.management.http.routes {
  cluster-management = ""
}
```

## Health checks

A cluster membership @ref:[health check](healthchecks.md) is included that is designed to be used as a readiness check.

By default the health check returns `true` when a node is either `Up` or `WeaklyUp`. Can be configured with `akka.management.cluster.heath-checks.ready-states`.

The cluster membership readiness check can be disabled with configuration:

```
akka.management.health-checks.readiness-checks {
  cluster-membership = ""
}
```
