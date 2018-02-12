#!/usr/bin/env bash

kubectl delete pods,deployment -l app=appka
kubectl delete services appka-service
