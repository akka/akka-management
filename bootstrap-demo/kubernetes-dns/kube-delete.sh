#!/usr/bin/env bash

kubectl delete services,pods,deployment -l appName=akka-cluster-kubernetes
kubectl delete services,pods,deployment akka-cluster-kubernetes
