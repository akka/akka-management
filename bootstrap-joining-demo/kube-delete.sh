#!/usr/bin/env bash

kubectl delete services,pods,deployment -l app=appka
kubectl delete services,pods,deployment appka-service
