DNS lookup example
==================

Build and publish docker image into the local repo.
`sbt docker:publishLocal`

Tag built image:
`docker tag integration-test-dns-api:1.0 <dockerhub-id>/integration-test-dns-api:1.0`

Push image into DockerHub 
`docker push <dockerhub-id>/integration-test-dns-api:1.0`

Mesosphere DC/OS
================

Host mode
---------
Use `marathon/app.host-mode.json` service description.

Bridge mode
-----------

Use `marathon/app.bridge-mode.json` service description.

