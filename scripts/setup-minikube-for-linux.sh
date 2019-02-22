#!/bin/bash

set -exu

# MINIKUBE_VERSION="latest" -- https://github.com/kubernetes/minikube/issues/2704
MINIKUBE_VERSION="latest"

# From https://github.com/kubernetes/minikube#linux-continuous-integration-without-vm-support
curl -Lo minikube https://storage.googleapis.com/minikube/releases/${MINIKUBE_VERSION}/minikube-linux-amd64 && chmod +x minikube && sudo cp minikube /usr/local/bin/ && rm minikube
curl -Lo kubectl https://storage.googleapis.com/kubernetes-release/release/$(curl -s https://storage.googleapis.com/kubernetes-release/release/stable.txt)/bin/linux/amd64/kubectl && chmod +x kubectl && sudo cp kubectl /usr/local/bin/ && rm kubectl

export MINIKUBE_WANTUPDATENOTIFICATION=false
export MINIKUBE_WANTREPORTERRORPROMPT=false
export MINIKUBE_HOME=$HOME
export CHANGE_MINIKUBE_NONE_USER=true
mkdir -p $HOME/.kube
touch $HOME/.kube/config

sudo ls -la $HOME/.kube/
sudo chown -R $USER $HOME/.kube
sudo chgrp -R $USER $HOME/.kube
sudo ls -la $HOME/.kube/

sudo ls -la $HOME/.minikube/ 2> /dev/null
sudo chown -R $USER $HOME/.minikube 2> /dev/null
sudo chgrp -R $USER $HOME/.minikube 2> /dev/null
sudo ls -la $HOME/.minikube/ 2> /dev/null

export KUBECONFIG=$HOME/.kube/config
sudo -E minikube start --vm-driver=none
sudo -E minikube addons enable ingress

# this for loop waits until kubectl can access the api server that Minikube has created
set +e
for i in {1..150}; do # timeout for 5 minutes
    sudo ls -la $HOME/.minikube/ 2> /dev/null
    kubectl get po &> /dev/null
    if [ $? -ne 1 ]; then
        break
    fi
    sleep 2
done

# kubectl commands are now able to interact with Minikube cluster
minikube version
minikube addons list
kubectl -n kube-system get deploy | grep dns
