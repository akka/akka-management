#!/bin/bash

set -exu

export NAMESPACE=appka-1
export APP_NAME=appka
export PROJECT_NAME=akka-sample-cluster-kubernetes-scala
export DEPLOYMENT=samples/akka-sample-cluster-kubernetes-scala/kubernetes/akka-cluster.yml

integration-test/scripts/kubernetes-test.sh

