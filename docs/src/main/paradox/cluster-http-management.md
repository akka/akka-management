<a id="http-cluster-management"></a>
# Cluster HTTP Management

Akka Management Cluster HTTP is a Management extension that allows interaction with an `akka-cluster` through an HTTP interface.
This management extension exposes different operations to manage nodes in a cluster as well as health checks based
on the cluster state.

The operations exposed are comparable to the Command Line Management tool or the JMX interface `akka-cluster` provides.

## Project Info

@@project-info{ projectId="cluster-http" }

## Dependencies

The Akka Cluster HTTP Management is a separate jar file.
Make sure to include it along with the core akka-management library in your project.

@@@note
The Akka dependencies are available from Akka’s secure library repository. To access them you need to use a secure, tokenized URL as specified at https://account.akka.io/token.
@@@

Additionally, add the dependencies as below.

@@dependency[sbt,Gradle,Maven] {
  symbol1=AkkaManagementVersion
  value1="$project.version$"
  group=com.lightbend.akka.management
  artifact=akka-management_$scala.binary.version$
  version=AkkaManagementVersion
  group2=com.lightbend.akka.management
  artifact2=akka-management-cluster-http_$scala.binary.version$
  version2=AkkaManagementVersion
}

Akka Cluster HTTP Management can be used with Akka $akka.version$ or later.
You have to override the following Akka dependencies by defining them explicitly in your build and
define the Akka version to the one that you are using. Latest patch version of Akka is recommended and
a later version than $akka.version$ can be used.

@@dependency[sbt,Gradle,Maven] {
  symbol=AkkaVersion
  value=$akka.version$
  group=com.typesafe.akka
  artifact=akka-cluster-sharding_$scala.binary.version$
  version=AkkaVersion
  group2=com.typesafe.akka
  artifact2=akka-discovery_$scala.binary.version$
  version2=AkkaVersion
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

| Path                         | HTTP method | Required form fields                 | Description
| ---------------------------- | ----------- | ------------------------------------ | -----------
| `/cluster/`                  | PUT         | operation: Prepare-for-full-shutdown | Executes a prepare for full shutdown operation in cluster.
| `/cluster/domain-events`     | GET         | None                                 | Returns cluster domain events as they occur, in JSON-encoded SSE format.
| `/cluster/members/`          | GET         | None                                 | Returns the status of the Cluster in JSON format.
| `/cluster/members/`          | POST        | address: `{address}`                 | Executes join operation in cluster for the provided `{address}`.
| `/cluster/members/{address}` | GET         | None                                 | Returns the status of `{address}` in the Cluster in JSON format.
| `/cluster/members/{address}` | DELETE      | None                                 | Executes leave operation in cluster for provided `{address}`.
| `/cluster/members/{address}` | PUT         | operation: Down                      | Executes down operation in cluster for provided `{address}`.
| `/cluster/members/{address}` | PUT         | operation: Leave                     | Executes leave operation in cluster for provided `{address}`.
| `/cluster/shards/{name}`     | GET         | None                                 | Returns shard info for the shard region with the provided `{name}`

The expected format of `address` follows the Cluster URI convention. Example: `akka://Main@myhostname.com:3311`

In the paths `address` is also allowed to be provided without the protocol prefix. Example: `Main@myhostname.com:3311`

### Get /cluster/domain-events request query parameters

| Query Parameter | Description
| --------------- | -----------
| type            | Optional. Specify event type(s) to include in response (see table). If not specified, all event types are included.

| Event Type                  | Description
| --------------------------- | -----------
| ClusterDomainEvent          | cluster domain event (parent)
| MemberEvent                 | membership event (parent)
| MemberJoined                | membership event - joined
| MemberWeaklyUp              | membership event - transitioned to WeaklyUp
| MemberUp                    | membership event - transitioned to Up
| MemberLeft                  | membership event - left
| MemberExited                | membership event - exited
| MemberDowned                | membership event - downed
| MemberRemoved               | membership event - removed
| LeaderChanged               | cluster's leader has changed
| RoleLeaderChanged           | cluster's role leader has changed
| ClusterShuttingDown         | cluster is shutting down
| ReachabilityEvent           | reachability event (parent)
| UnreachableMember           | reachability event - member now unreachable
| ReachableMember             | reachability event - member now reachable
| DataCenterReachabilityEvent | DC reachability event (parent)
| UnreachableDataCenter       | DC reachability event - DC now unreachable
| ReachableDataCenter         | DC reachability event - DC now reachable

Example request:

    GET /cluster/domain-events?type=MemberUp&type=LeaderChanged HTTP/1.1
    Host: 192.168.1.23:8558

Example response:

    HTTP/1.1 200 OK
    Server: akka-http/10.2.2
    Date: Mon, 11 Jan 2021 21:02:37 GMT
    Transfer-Encoding: chunked
    Content-Type: text/event-stream

    data:{"member":{"dataCenter":"default","roles":["dc-default"],"status":"Up","uniqueAddress":{"address":"akka://default@127.0.0.1:2551","longUid":-2440990093160003086}},"type":"MemberUp"}
    event:MemberUp

    data:{"address":"akka://default@127.0.0.1:2551","type":"LeaderChanged"}
    event:LeaderChanged

### Get /cluster/domain-events responses

| Response code | Description
| ------------- | -----------
| 200           | Cluster events in Server-Sent-Event format (JSON)
| 500           | Something went wrong.

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

### Get /cluster/shard responses

| Response code | Description
| ------------- | -----------
| 200           | Shard entity type keys in JSON format

Example response:

{
  "entityTypeKeys": ["ShoppingCart"]
}

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

By default the health check returns `true` when a node is either `Up` or `WeaklyUp`. Can be configured with `akka.management.cluster.health-checks.ready-states`.

The cluster membership readiness check can be disabled with configuration:

```
akka.management.health-checks.readiness-checks {
  cluster-membership = ""
}
```
