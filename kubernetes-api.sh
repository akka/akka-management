#!/usr/bin/env bash

echo Running integration test for kubernetes-api

# travis should hire these
echo docker username is $DOCKER_USERNAME and password is $DOCKER_PASSWORD
docker login --username $DOCKER_USERNAME --password $DOCKER_PASSWORD
sbt "bootstrap-joining-demo-kubernetes-api/docker:publishLocal"
docker images
docker tag akka-management-bootstrap-joining-demo-kubernetes-api:1.3.3.7 sebastianharko/akka-management-bootstrap-joining-demo-kubernetes-api:1.3.3.7
docker push sebastianharko/akka-management-bootstrap-joining-demo-kubernetes-api:1.3.3.7
 
