#!/bin/bash

set -exu

export NAMESPACE=akka-rollingupdate-demo-ns
export APP_NAME=akka-rollingupdate-demo
export PROJECT_NAME=integration-test-rollingupdate-kubernetes
export CRD=rolling-update-kubernetes/pod-cost.yml
export DEPLOYMENT=integration-test/rollingupdate-kubernetes/kubernetes/akka-cluster-cr.yml

integration-test/scripts/rollingupdate-kubernetes-cr-test.sh

