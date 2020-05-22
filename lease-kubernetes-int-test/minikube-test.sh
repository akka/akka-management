#!/bin/bash

# For now this assumed there is a minikube running locally, with kubectl on the path.

set -exu

JOB_NAME=lease-test
PROJECT_DIR=lease-kubernetes-int-test

eval $(minikube -p minikube docker-env)
sbt "lease-kubernetes-int-test / docker:publishLocal"

kubectl apply -f $PROJECT_DIR/kubernetes/rbac.yml
kubectl delete -f $PROJECT_DIR/kubernetes/job.yml || true

for i in {1..10}
do
  echo "Waiting for old jobs to be deleted"
  [ `kubectl get jobs | grep $JOB_NAME | wc -l` -eq 0 ] && break
  sleep 3
done

echo "Old jobs cleaned up. Creating new job"

kubectl create -f $PROJECT_DIR/kubernetes/job.yml

# Add in a default sleep when we know a min amount of time it'll take

for i in {1..10}
do
  echo "Checking for job completion"
  # format is: wait for 1/1
  # lease-test   1/1           10s        2m32s
  [ `kubectl get jobs | grep $JOB_NAME | awk '{print $2}'` == "1/1" ] && break
  sleep 5
done

echo "Logs for job run:"
echo "=============================="

pods=$(kubectl get pods --selector=job-name=$JOB_NAME --output=jsonpath={.items..metadata.name})
echo "Pods: $pods"
for pod in $pods
do
 echo "Logging for $pod"
  kubectl logs $pod
done

if [ $i -eq 10 ]
then
  kubectl get jobs
  kubectl describe job $JOB_NAME
  echo "[ERROR] Job did not complete successfully. See logs above."
  exit -1
fi


