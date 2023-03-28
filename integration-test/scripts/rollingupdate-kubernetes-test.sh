#!/bin/bash -e

eval $(minikube -p minikube docker-env)
sbt $PROJECT_NAME/docker:publishLocal

docker images | head

kubectl create namespace $NAMESPACE || true
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
  exit -1
fi

max_tries=10
try_count=0
# Declare a map to store the annotated values for each pod
declare -a pod_annotation_array

# Loop until all pods are annotated or the maximum number of tries is reached
while true
do
  # Get the list of pods matching the namespace and app name, and are in the Running state
  pod_list=$(kubectl get pods -n $NAMESPACE | grep $APP_NAME | grep Running | awk '{ print $1 }')
  annotated_count=0

  for pod_name in $pod_list
  do
    # Get the value of the annotation for the pod
    annotation_value=$(kubectl describe pod $pod_name -n $NAMESPACE | grep controller.kubernetes.io/pod-deletion-cost | awk '{print $3}')

    # Check if the annotation value is set or empty
    if [ -z "$annotation_value" ]
    then
      echo "The annotation value for pod $pod_name is empty"
    else
      echo "The annotation value for pod $pod_name is set to $annotation_value"
      annotated_count=$((annotated_count+1))
      # Store the annotated value for the pod in the map
      pod_annotation_array+=("$annotation_value")
    fi
  done

  if [ $annotated_count -eq $(echo $pod_list | wc -w) ]
  then
    echo "All pods were annotated successfully!"
    break
  fi

  # Increment the try count and check if the maximum number of tries is reached
  try_count=$((try_count+1))
  if [ $try_count -eq $max_tries ]
  then
    echo "Maximum number of tries reached, not all pods are annotated"

    echo "Logs"
    echo "=============================="
    for POD in $(kubectl get pods -n $NAMESPACE | grep $APP_NAME | grep Running | awk '{ print $1 }')
    do
      echo "Logging for $POD"
      kubectl logs $POD -n $NAMESPACE
    done

    exit 1
  fi

  # Wait for 10 seconds before trying again
  sleep 10
done

# Get the name of the pod with the highest annotated value
highest_annotated_index=$(echo "${pod_annotation_array[@]}" | tr ' ' '\n' | nl -v 1 | sort -nr -k 2 | head -n 1 | awk '{ print $1 }')
highest_annotated_pod=$(echo $pod_list | cut -d ' ' -f $highest_annotated_index)

# Scale down the cluster to one pod
kubectl scale deployment $APP_NAME -n $NAMESPACE --replicas=1

# Wait for the deployment to be scaled down
for ((try_count=0; try_count<max_tries; try_count++)); do
  pod_count=$(kubectl get pods -n $NAMESPACE | grep $APP_NAME | grep Running | wc -l)
  if [ $pod_count -eq 1 ]
  then
    echo "Cluster scaled down to one pod"
    break
  fi
  sleep 5
done
if [[ $try_count -ge $max_tries ]]; then
  echo "Exceeded max retries, aborting"
  exit 1
fi

# Get the name of the remaining pod
remaining_pod=$(kubectl get pods -n $NAMESPACE | grep $APP_NAME | grep Running | awk '{ print $1 }')

# Compare the remaining pod with the highest annotated pod
if [ "$remaining_pod" = "$highest_annotated_pod" ]
then
  echo "Success: The remaining pod $remaining_pod is the highest annotated pod!"
else
  echo "Error: The remaining pod ($remaining_pod) is not the same as the highest annotated pod ($highest_annotated_pod)"
  exit 1
fi
