DNS lookup example
===

Build and publish docker image into the local repo.
`sbt docker:publishLocal`

Tag built image:
`docker tag bootstrap-joining-demo-dns-api:1.0 <dockerhub-id>/bootstrap-joining-demo-dns-api:1.0`

Push image into DockerHub 
`docker push <dockerhub-id>/bootstrap-joining-demo-dns-api:1.0`

Mesosphere DC/OS
===

Use `mesos.host-mode.json` service description.