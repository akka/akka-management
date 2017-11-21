#!/usr/bin/env bash

# this is for getting a specific log, not just by the label
POD=$(kubectl get pods | grep appka | grep Running | head -n1 | awk '{ print $1 }')

# this checks that DNS works
kubectl exec -it $POD -- nslookup kubernetes.default

# checks if kube dns is running:
kubectl get pods --namespace=kube-system -l k8s-app=kube-dns
# checks if the svc service is up
kubectl get svc --namespace=kube-system

#ktoso @ 三日月~/code/akka-management/joining [wip-joining*]
#$ kubectl exec -it $POD -- dig +short NS
#a.root-servers.net.
#b.root-servers.net.
#c.root-servers.net.
#d.root-servers.net.
#e.root-servers.net.
#f.root-servers.net.
#g.root-servers.net.
#h.root-servers.net.
#i.root-servers.net.
#j.root-servers.net.
#k.root-servers.net.
#l.root-servers.net.
#m.root-servers.net.

# in general, read this: https://kubernetes.io/docs/concepts/services-networking/dns-pod-service/#srv-records
