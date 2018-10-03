#!/bin/bash

set -exu

sbt bootstrap-demo-kubernetes-api/docker:publishLocal

kubectl apply -f bootstrap-demo/kubernetes-api/kubernetes/akka-cluster.yml

TRY=0

until [ $TRY -ge 10 ]
do
  echo "Waiting for pods to get ready..."
  kubectl get pods
  [ `kubectl get pods | grep Running | wc -l` -eq 2 ] && break
  sleep 4
done

if [ $TRY -eq 10 ]
then
  echo "Pods did not get ready"
  exit -1
fi

POD=$(kubectl get pods | grep appka | grep Running | head -n1 | awk '{ print $1 }')

TRY=0

until [ $TRY -ge 10 ]
do
  echo "Checking for MemberUp logging..."
  kubectl logs $POD | grep MemberUp
  [ `kubectl logs $POD | grep MemberUp | wc -l` -eq 2 ] && break
  sleep 3
done

if [ $TRY -eq 10 ]
then
  echo "No 2 MemberUp log events found"
  exit -1
fi
