#!/usr/bin/env bash

echo Running integration test for kubernetes-api

docker login --username $DOCKER_USERNAME --password $DOCKER_PASSWORD
sbt "bootstrap-joining-demo-kubernetes-api/docker:publishLocal"
docker images
 
