<a id="http-cluster-management"></a>
# Cluster Http Management

Akka Cluster HTTP Management is module that allows you interaction with an `akka-cluster` through an HTTP interface.
This module exposes different operations to manage nodes in a cluster. 

The operations exposed are comparable to the Command Line Management tool or the JMX interface `akka-cluster` provides.

## Preparing your project for Cluster HTTP Management

The Akka Cluster HTTP Management is a separate jar file. Make sure that you have the following dependency in your project::

  "com.typesafe.akka" %% "akka-cluster-http-management" % "@version@" @crossString@


## API Definition

The following table describes the usage of the API:

| Path | HTTP method | Required form fields | Responses | Description |
| ---- | ----------- | -------------------- | --------- | ----------- |
| `/members/` | GET | None | See below | Returns the status of the Cluster in JSON format.
| `/members/` | POST | address: {address} | See below | Executes join operation in cluster for the provided `{address}`.
| `/members/{address}` | GET  | None | See below | Returns the status of `{address}` in the Cluster in JSON format.
| `/members/{address}` | DELETE | None | See below | Executes leave operation in cluster for provided `{address}`.
| `/members/{address}` | PUT | operation: Down | See below | Executes down operation in cluster for provided `{address}`.
| `/members/{address}` | PUT | operation: Leave | See below | Executes leave operation in cluster for provided `{address}`.

The expected format of `address` follows the Cluster URI convention. Example: `akka://Main@myhostname.com:3311`

### Get /members responses

| Response code | Description |
| ------------- | ----------- |
| 200 | Status of cluster in JSON format |
| 500 | Something went wrong. Cluster might be shutdown.|
 
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
       "unreachable": []
     }

### Post /members responses

| Response code | Description |
| ------------- | ----------- |
| 200 | Executing join operation. |
| 500 | Something went wrong. Cluster might be shutdown.| 

Example response:

    Joining ${address}

### Get /members/{address} responses

| Response code | Description |
| ------------- | ----------- |
| 200 | Status of cluster in JSON format |
| 404 | No member was found in the cluster for the given `{address}`. |
| 500 | Something went wrong. Cluster might be shutdown.| 

Example response:

    {
      "node": "akka.tcp://test@10.10.10.10:1111",
      "nodeUid": "-169203556",
      "status": "Up",
      "roles": []
    }

### Delete /members/{address} responses

| Response code | Description |
| ------------- | ----------- |
| 200 | Executing leave operation. |
| 404 | No member was found in the cluster for the given `{address}`. |
| 500 | Something went wrong. Cluster might be shutdown.| 

Example response:

    Leaving ${address}

### Put /members/{address} responses

| Response code | Operation | Description |
| ------------- | --------- | ----------- |
| 200 | Down  | Executing down operation. |
| 200 | Leave | Executing leave operation. |
| 400 | - | Operation supplied in `operation` form field is not supported. |
| 404 | - | No member was found in the cluster for the given `{address}` |
| 500 | - | Something went wrong. Cluster might be shutdown.| 

Example response:

    Downing ${address}

## Configuration

You can configure hostname and port to use for the HTTP Cluster management by overriding the following:

    akka.cluster.http.management.hostname = "127.0.0.1"
    akka.cluster.http.management.port = 19999

However, those are the values by default. In case you are running multiple cluster instances within the same JVM these 
configuration parameters should allow you to expose different cluster management APIs by modifying the port:

    //Config Actor system 1
    akka.cluster.http.management.hostname = "127.0.0.1"
    akka.cluster.http.management.port = 19999
    
    //Config Actor system 2
    akka.cluster.http.management.hostname = "127.0.0.1"
    akka.cluster.http.management.port = 20000
    
    ... 
    
    val actorSystem1 = ActorSystem("as1", config1)
    val cluster1 = Cluster(actorSystem1)
    val actorSystem2 = ActorSystem("as2", config2)
    val cluster2 = Cluster(actorSystem2)

    ClusterHttpManagement(cluster1).start()
    ClusterHttpManagement(cluster2).start()

It is also possible to modify the default root path of the API (`members/`). Provide your desired path when starting:

    ClusterHttpManagement(cluster, "myClusterName").start()


## Security

> **Note:**
This module does not provide security by default. It's the developer's choice to add security to this API.

The non-secured usage of the module is as follows:

    ClusterHttpManagement(cluster).start()

### Enabling SSL for Cluster HTTP Management

To enable SSL you need to provide an `SSLContext`. You can find more information about it in @ref:[Server side https support](http/server-side-https-support.md)

    val https: HttpsConnectionContext = ConnectionContext.https(sslContext)
    ClusterHttpManagement(cluster, https).start()

### Enabling Basic Authentication for Cluster HTTP Management

To enable Basic Authentication you need to provide an authenticator. You can find more information in @ref:[Authenticate Basic Async directive](http/routing-dsl/directives/security-directives/authenticateBasicAsync.md)

    def myUserPassAuthenticator(credentials: Credentials): Future[Option[String]] =
      credentials match {
        case p @ Credentials.Provided(id) =>
          Future {
            // potentially
            if (p.verify("p4ssw0rd")) Some(id)
            else None
          }
        case _ => Future.successful(None)
      }
      
    ClusterHttpManagement(cluster, myUserPassAuthenticator(_)).start()  

### Enabling SSL and Basic Authentication for Cluster HTTP Management

To enable SSL and Basic Authentication you need to provide both an `SSLContext` and an authenticator.

    def myUserPassAuthenticator(credentials: Credentials): Future[Option[String]] =
      credentials match {
        case p @ Credentials.Provided(id) =>
          Future {
            // potentially
            if (p.verify("p4ssw0rd")) Some(id)
            else None
          }
        case _ => Future.successful(None)
      }
      
    val https: HttpsConnectionContext = ConnectionContext.https(sslContext)
    ClusterHttpManagement(cluster, myUserPassAuthenticator(_), https).start()  

## Stopping Cluster HTTP Management

In a dynamic environment you might want to start and stop multiple instances of HTTP Cluster Management.
You can do so by calling `stop()` on `ClusterHttpManagement`. This method return a `Future[Done]` to inform when the 
module has been stopped.

    val httpClusterManagement = ClusterHttpManagement(cluster)
    httpClusterManagement.start()
    //...
    val bindingFuture = httpClusterManagement.stop()
    bindingFuture.onComplete { _ => println("It's stopped") }
    