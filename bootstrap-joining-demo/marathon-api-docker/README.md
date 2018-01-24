Marathon API Docker lookup example
==================================

This example demonstrates Marathon API discovery mechanism when an application runs inside a docker container in 
network bridge mode with automatic host port allocation.

Marathon API discovery mechanism uses Marathon API to find other application instances (contact points) to form Akka cluster.
In order to identify contact points it uses a label assigned to the application in the application description and 
a port name to identify Akka HTTP management port:

Label need to be defined in two places. 
First place is the application configuration:
see `bootstrap-joining-demo/marathon-api-docker/src/main/resources/application.conf`:
```
    akka.management.cluster.bootstrap.contact-point-discovery.effective-name = "marathon-api-docker-app-label"
```

> NOTE: if `effective-name` is not defined explicitly then Discovery Mechanism will use concatenation of
> `service-name` and `.service-namespace` if any of them defined in the application config. Otherwise, it will use
> the application ActorSystem name.

The second place is the application description:
see `bootstrap-joining-demo/marathon-api-docker/marathon/marathon-api-docker-app.json`:
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
see `bootstrap-joining-demo/marathon-api-docker/src/main/resources/application.conf`:
```
...
akka.discovery.marathon-api.app-port-name = "akkamgmthttp"
... 
``` 

it should match with Akka HTTP management port name in Marathon application description docker port declaration:
see `bootstrap-joining-demo/marathon-api-docker/marathon/marathon-api-docker-app.json`:
```
...
  "container": {
    "type": "DOCKER",
    "docker": {
      ...
      "portMappings": [
        ...
        {
          "containerPort": 19999,
          "hostPort": 0,
          "servicePort": 10206,
          "protocol": "tcp",
          "name": "akkamgmthttp"
...
```

Build
-----

Build and publish docker image into the local repo.
`sbt docker:publishLocal`

Set your $DOCKER_HUB_ID:
`export DOCKER_HUB_ID=<your docker hub id>`

Tag built image:
`docker tag bootstrap-joining-demo-marathon-api-docker:1.0 $DOCKER_HUB_ID/bootstrap-joining-demo-marathon-api-docker:1.0`

Push image into DockerHub 
`docker push $DOCKER_HUB_ID/bootstrap-joining-demo-marathon-api-docker:1.0`


