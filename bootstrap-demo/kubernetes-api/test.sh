#!/bin/bash

set -exu

sbt bootstrap-demo-kubernetes-api/docker:publishLocal

kubectl create namespace akka-bootstrap
kubectl apply -f bootstrap-demo/kubernetes-api/kubernetes/akka-cluster.yml

for i in {1..10}
do
  echo "Waiting for pods to get ready..."
  kubectl get pods --namespace akka-bootstrap
  [ `kubectl get pods --namespace akka-bootstrap | grep Running | wc -l` -eq 3 ] && break
  sleep 4
done

if [ $i -eq 10 ]
then
  echo "Pods did not get ready"
  exit -1
fi

POD=$(kubectl get pods --namespace akka-bootstrap | grep akka-bootstrap-demo | grep Running | head -n1 | awk '{ print $1 }')

for i in {1..10}
do
  echo "Checking for MemberUp logging..."
  kubectl logs $POD --namespace akka-bootstrap | grep MemberUp || true
  [ `kubectl logs --namespace akka-bootstrap $POD | grep MemberUp | wc -l` -eq 3 ] && break
  sleep 3
done

if [ $i -eq 10 ]
then
  echo "No 3 MemberUp log events found"
  echo "=============================="
  for POD in $(kubectl get pods | grep akka-bootstrap-demo | grep Running | awk '{ print $1 }')
  do
   echo "Logging for $POD"
    kubectl logs $POD bootstrap-demo-kubernetes-dns
  done
  exit -1
fi

