DNS lookup example
==================

Build and publish docker image into the local repo.
`sbt docker:publishLocal`

Tag built image:
`docker tag bootstrap-joining-demo-dns-api:1.0 <dockerhub-id>/bootstrap-joining-demo-dns-api:1.0`

Push image into DockerHub 
`docker push <dockerhub-id>/bootstrap-joining-demo-dns-api:1.0`

Mesosphere DC/OS
================

Host mode
---------
Use `marathon/app.host-mode.json` service description.

Bridge mode
-----------

Use `marathon/app.bridge-mode.json` service description.

Please note, `discovery-dns` extension doesn't support DNS SRV lookup at the moment, thus a fixed HTTP management host port provided.

