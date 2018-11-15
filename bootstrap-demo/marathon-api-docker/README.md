Marathon API Docker lookup example
==================================

This example demonstrates Marathon API discovery mechanism when an application runs inside a docker container in 
network bridge mode with automatic host port allocation.

Marathon API discovery mechanism uses Marathon API to find other application instances (contact points) to form Akka cluster.
In order to identify contact points it uses a label assigned to the application in the application description and 
a port name to identify Akka HTTP management port:

Label need to be defined in two places. 
First place is the application configuration:
see `bootstrap-demo/marathon-api-docker/src/main/resources/application.conf`:
```
    akka.management.cluster.bootstrap.contact-point-discovery.effective-name = "marathon-api-docker-app-label"
```

> NOTE: if `effective-name` is not defined explicitly then Discovery Mechanism will use concatenation of
> `service-name` and `.service-namespace` if any of them defined in the application config. Otherwise, it will use
> the application ActorSystem name.

The second place is the application description:
see `marathon/marathon-api-docker-app.json`:
```
  ...
  "labels": {
    "ACTOR_SYSTEM_NAME": "marathon-api-docker-app-label"
  }
  ...

```

> NOTE: Discovery Mechanism uses `ACTOR_SYSTEM_NAME` label name as a part of Marathon API query param to find relevant contact points.
> This default value can be overridden in the application config `akka.discovery.marathon-api.app-label-query`

After Discovery mechanism found potential contact points by the label it needs to find Akka HTTP management port.

It uses `app-port-name`:
see `src/main/resources/application.conf`:
```
...
akka.discovery.marathon-api.app-port-name = "akkamgmthttp"
... 
``` 

it should match with Akka HTTP management port name in Marathon application description docker port declaration:
see `marathon/marathon-api-docker-app.json`:
```
...
  "container": {
    "type": "DOCKER",
    "docker": {
      ...
      "portMappings": [
        ...
        {
          "containerPort": 8558,
          "hostPort": 0,
          "servicePort": 10206,
          "protocol": "tcp",
          "name": "akkamgmthttp"
...
```

How to Build
------------

1. Set DOCKER_USER env variable and authenticated to your docker hub account:

`export DOCKER_USER=<your-docker-hub-account>`

2. In order to build and publish this example into your docker hub repo run next command from `akka-management` work folder

`sbt bootstrap-demo-marathon-api-docker/docker:publish`

How to Deploy
-------------

Use next template for Marathon application descriptor. 

`marathon/marathon-api-docker-app.json`

> NOTE: Make sure to substitute $DOCKER_USER in it to point to your docker hub repo.

