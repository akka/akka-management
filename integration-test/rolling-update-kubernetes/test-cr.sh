#!/bin/bash

set -exu

export NAMESPACE=akka-rolling-update-demo-cr-ns
export APP_NAME=akka-rolling-update-demo
export PROJECT_NAME=integration-test-rolling-update-kubernetes
export CRD=rolling-update-kubernetes/pod-cost.yml
export DEPLOYMENT=integration-test/rolling-update-kubernetes/kubernetes/akka-cluster-cr.yml

integration-test/scripts/rolling-update-kubernetes-cr-test.sh

