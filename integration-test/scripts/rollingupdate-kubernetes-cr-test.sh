#!/bin/bash -e

eval $(minikube -p minikube docker-env)
sbt $PROJECT_NAME/docker:publishLocal

docker images | head

kubectl create namespace $NAMESPACE || true
kubectl apply -f $CRD
kubectl -n $NAMESPACE delete deployment akka-rollingupdate-demo || true
kubectl -n $NAMESPACE apply -f $DEPLOYMENT

for i in {1..10}
do
  echo "Waiting for pods to get ready..."
  kubectl get pods -n $NAMESPACE
  [ `kubectl get pods -n $NAMESPACE | grep Running | wc -l` -eq 3 ] && break
  sleep 4
done

if [ $i -eq 10 ]
then
  echo "Pods did not get ready"
  kubectl -n $NAMESPACE describe deployment akka-rollingupdate-demo
  exit -1
fi

max_tries=10
try_count=0

# Loop until all pods are included or the maximum number of tries is reached
while true
do
  # Get the list of pods matching the namespace and app name, and are in the Running state
  pod_list=$(kubectl get pods -n $NAMESPACE | grep $APP_NAME | grep Running | awk '{ print $1 }' | sort)

  # Get the pods in the CR
  cr_pod_list=$(kubectl describe podcosts.akka.io akka-rollingupdate-demo -n $NAMESPACE | grep "Pod Name" | awk '{print $3}' | sort)

  if [ "$pod_list" = "$cr_pod_list" ]
    then
      echo "Found expected pods in CR: $cr_pod_list"
      break
  else
      echo "Expected $pod_list, but didn't find expected pods in CR: $cr_pod_list"
  fi

  for pod_name in $pod_list
  do
    # Get the pod names from the cr
    cr_pod_list=$(kubectl describe podcosts.akka.io akka-rollingupdate-demo -n $NAMESPACE | grep "Pod Name" | awk '{print $3}' | sort -z)

    # Check if the annotation value is set or empty
    if ["$pod_list" == "$cr_pod_list" ]
    then
      echo "Found expected pods in CR: $cr_pod_list"
    else
      echo "Didn't find expected pods in CR: $cr_pod_list"
    fi
  done

  # Wait for 10 seconds before trying again
  sleep 10
done

if [[ $try_count -ge $max_tries ]]; then
  echo "Exceeded max retries, aborting"
  exit 1
fi
