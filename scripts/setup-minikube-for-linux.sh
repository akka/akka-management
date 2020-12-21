#!/bin/bash

set -exu

# using Minikube v1.16.0 fails
MINIKUBE_VERSION="v1.15.1"

# From https://minikube.sigs.k8s.io/docs/tutorials/continuous_integration/
curl -Lo minikube https://storage.googleapis.com/minikube/releases/${MINIKUBE_VERSION}/minikube-linux-amd64 && chmod +x minikube && sudo cp minikube /usr/local/bin/ && rm minikube
curl -Lo kubectl https://storage.googleapis.com/kubernetes-release/release/$(curl -s https://storage.googleapis.com/kubernetes-release/release/stable.txt)/bin/linux/amd64/kubectl && chmod +x kubectl && sudo cp kubectl /usr/local/bin/ && rm kubectl

export MINIKUBE_WANTUPDATENOTIFICATION=false
export MINIKUBE_WANTREPORTERRORPROMPT=false
export MINIKUBE_HOME=$HOME
export CHANGE_MINIKUBE_NONE_USER=true
mkdir -p $HOME/.kube
touch $HOME/.kube/config

export KUBECONFIG=$HOME/.kube/config
minikube start --driver=docker
minikube addons enable ingress
#sudo -E chmod a+r ~/.minikube/client.key

# this for loop waits until kubectl can access the api server that Minikube has created
set +e
for i in {1..150}; do # timeout for 5 minutes
    kubectl get po &> /dev/null
    if [ $? -ne 1 ]; then
        break
    fi
    sleep 2
done

# kubectl commands are now able to interact with Minikube cluster

minikube version
minikube addons list

eval $(minikube -p minikube docker-env)
kubectl -n kube-system get deploy | grep dns
