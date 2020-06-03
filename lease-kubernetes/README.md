# Discovering the K8s API

K8s supports swagger. To enable for minikube start with the following option:

```
minikube start --extra-config=apiserver.enable-swagger-ui=true
```

Then start a proxy locally to the API server:

```
kubectl proxy --port=8080
```

Then visit:

```
http://localhost:8080/swagger-ui/
```


