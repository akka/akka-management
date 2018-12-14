#!/bin/bash

set -exu

VERSION=`sbt publishM2 | grep akka-management-cluster-bootstrap_2.12 | tail -1 | cut -d "/" -f 11`

cd bootstrap-demo/kubernetes-api-java

mvn -Dakka-management.version=$VERSION clean package docker:build

kubectl create namespace akka-bootstrap
kubectl apply -f kubernetes/akka-cluster.yml

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
  [ `kubectl logs $POD --namespace akka-bootstrap | grep MemberUp | wc -l` -eq 3 ] && break
  sleep 3
done

if [ $i -eq 10 ]
then
  echo "No 3 MemberUp log events found"
  kubectl get pods --namespace akka-bootstrap
  echo "=============================="
  for POD in $(kubectl get pods --namespace akka-bootstrap | grep akka-bootstrap-demo | grep Running | awk '{ print $1 }')
  do
   echo "Logging for $POD"
    kubectl logs $POD --namespace akka-bootstrap
  done
  exit -1
fi
