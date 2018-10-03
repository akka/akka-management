#!/bin/bash

set -exu

sbt bootstrap-demo-kubernetes-api/docker:publishLocal

kubectl apply -f bootstrap-demo/kubernetes-api/kubernetes/akka-cluster.yml

sleep 20

kubectl get pods

POD=$(kubectl get pods | grep appka | grep Running | head -n1 | awk '{ print $1 }')

kubectl logs $POD
