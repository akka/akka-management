#!/bin/bash

set -exu

export NAMESPACE=akka-bootstrap
export APP_NAME=akka-bootstrap-demo
export PROJECT_NAME=integration-test-kubernetes-dns
export DEPLOYMENT=integration-test/kubernetes-dns/kubernetes/akka-cluster.yml

integration-test/scripts/kubernetes-test.sh
