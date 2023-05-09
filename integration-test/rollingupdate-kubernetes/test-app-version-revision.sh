#!/bin/bash

set -exu

export NAMESPACE=akka-rollingupdate-demo-ns
export APP_NAME=akka-rollingupdate-demo
export PROJECT_NAME=integration-test-rollingupdate-kubernetes
export DEPLOYMENT=integration-test/rollingupdate-kubernetes/kubernetes/akka-cluster-app-value-revision.yml

integration-test/scripts/app-version-revision-kubernetes-test.sh

