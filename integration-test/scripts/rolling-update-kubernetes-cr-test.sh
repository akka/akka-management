#!/bin/bash -e

echo "Running rolling-update-kubernetes-cr-test.sh with deployment: $DEPLOYMENT"

eval $(minikube -p minikube docker-env)
sbt $PROJECT_NAME/Docker/publishLocal

docker images | head

kubectl create namespace $NAMESPACE || true
kubectl apply -f $CRD
kubectl -n $NAMESPACE delete deployment $APP_NAME || true
kubectl -n $NAMESPACE apply -f $DEPLOYMENT

for i in {1..20}
do
  echo "Waiting for pods to get ready..."
  kubectl get pods -n $NAMESPACE
  phase=$(kubectl get pods -o jsonpath="{.items[*].status.phase}" -n $NAMESPACE)
  status=$(kubectl get pods -o jsonpath="{.items[*].status.containerStatuses[*].ready}" -n $NAMESPACE)
  if [ "$phase" == "Running Running Running" ] && [ "$status" == "true true true" ]
  then
    break
  fi
  sleep 4
done

if [ $i -eq 20 ]
then
  echo "Pods did not get ready"
  kubectl events $APP_NAME -n $NAMESPACE
  kubectl describe deployment $APP_NAME -n $NAMESPACE

  echo ""
  echo "Logs from all $APP_NAME containers"
  kubectl logs -l app=$APP_NAME --all-containers=true -n $NAMESPACE || true

  echo ""
  echo "Logs from all previous $APP_NAME containers"
  kubectl logs -p -l app=$APP_NAME --all-containers=true -n $NAMESPACE || true

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
  cr_pod_list=$(kubectl describe podcosts.akka.io $APP_NAME -n $NAMESPACE | grep "Pod Name" | awk '{print $3}' | sort)

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
    cr_pod_list=$(kubectl describe podcosts.akka.io $APP_NAME -n $NAMESPACE | grep "Pod Name" | awk '{print $3}' | sort -z)

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
