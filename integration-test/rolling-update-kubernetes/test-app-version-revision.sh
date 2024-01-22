#!/bin/bash

set -exu

export NAMESPACE=akka-rolling-update-demo-ns
export APP_NAME=akka-rolling-update-demo
export PROJECT_NAME=integration-test-rolling-update-kubernetes
export DEPLOYMENT=integration-test/rolling-update-kubernetes/kubernetes/akka-cluster-app-value-revision.yml

integration-test/scripts/app-version-revision-kubernetes-test.sh

