#!/bin/bash -e

set -exu

echo "Running app-version-revision-kubernetes-test.sh with deployment: $DEPLOYMENT"

eval $(minikube -p minikube docker-env)

sbt $PROJECT_NAME/docker:publishLocal

# function to run after each change of ReplicaSet - see below `kubectl ...`
# Param $1 is the expected revision
testRevisionInPodsLog () {
  echo "Testing for revision $1:"
  for i in {1..20}
  do
    sleep 5
    echo "Waiting for rolling update to complete ...  (revision $1, $i/20)"
    kubectl get pods -n $NAMESPACE

    # the two lines below ensure a rollout is fully completed, before we look at the logs for updated revision
    # if not filtering out Terminated or pods that are not not ready (0/1) we will see previous revisions in the logs
    [ `kubectl get pods -n $NAMESPACE | grep Terminating | wc -l` -ne 0 ] && continue   # loop again until no Terminating nods in the list
    [ `kubectl get pods -n $NAMESPACE | grep 0/1 | wc -l` -eq 0 ] && break              # exit the loop once we only have READY (1/1) pods
  done

  if [ $i -eq 20 ]
  then
    echo "Pods did not get ready (revision $1)"
    kubectl -n $NAMESPACE describe deployment akka-rollingupdate-demo
    exit -1
  fi

  echo "Rolling update complete."

  # expected log to indicate that reading was successful
  expected_app_version_log="Reading revision from Kubernetes: akka.cluster.app-version was set to $1"

  for POD in $(kubectl get pods -n $NAMESPACE | grep $APP_NAME | awk '{ print $1 }')
  do
     # this grep'ed string is always logged
    app_version_log=$(kubectl logs $POD -n $NAMESPACE | grep 'revision from Kubernetes')
    echo "found log in $POD: $app_version_log"

    if [[ "$app_version_log" =~ .*"$expected_app_version_log".* ]]; then
      echo "Logging for $POD contains '$expected_app_version_log'."
    else
      echo "Error! Logging for $POD did not contain '$expected_app_version_log' but it contained '$app_version_log'."
      exit -1
    fi
  done

  echo "Testing for revision $1 done!"
}

# prep
docker images | head
kubectl create namespace $NAMESPACE || true
kubectl -n $NAMESPACE delete deployment akka-rollingupdate-demo || true
kubectl -n $NAMESPACE apply -f $DEPLOYMENT

# after the initial deployment
testRevisionInPodsLog "1"

# update the deployment, which creates a new revision
kubectl set env deployment/$APP_NAME SOME_ENV_TO_BE_CHANGED=on -n $NAMESPACE
testRevisionInPodsLog "2"

# rollback, which creates a new revision
kubectl rollout undo deployment/$APP_NAME -n $NAMESPACE
testRevisionInPodsLog "3"

echo "Test Successful!"