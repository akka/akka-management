# akka-sample-cluster-kubernetes-java
akka sample cluster with kubernetes discovery in scala

This is an example SBT project showing how to create an Akka Cluster on
Kubernetes.

## Kubernetes Instructions

## Starting

First, package the application and make it available locally as a docker image:

    mvn clean package docker:build

Then `akka-cluster.yml` should be sufficient to deploy a 2-node Akka Cluster, after
creating a namespace for it:

    kubectl apply -f kubernetes/namespace.json
    kubectl config set-context --current --namespace=appka-1
    kubectl apply -f kubernetes/akka-cluster.yml
    
To check what you have done in Kubernetes so far, you can do:

    kubectl get deployments
    kubectl get pods
    kubectl get replicasets
    kubectl cluster-info dump
    kubectl logs appka-79c98cf745-abcdee   # pod name

Finally, create a service so that you can then test [http://127.0.0.1:8080](http://127.0.0.1:8080)
for 'hello world':

    kubectl expose deployment appka --type=LoadBalancer --name=appka-service
    kubectl port-forward svc/appka-service 8080:8080
    
To wipe everything clean and start over, do:

    kubectl delete namespaces appka-1

## Running in a real Kubernetes cluster

#### Publish to a registry the cluster can access e.g. Dockerhub with the kubakka user

The app image must be in a registry the cluster can see. The build.sbt uses DockerHub by default.
Use `mvn -Ddocker.registry=$DOCKER_REPO_URL/$NAMESPACE` if your cluster can't access DockerHub.

To push an image to docker hub run:

    mvn -am -pl bootstrap-demo-kubernetes-api package docker:push

And remove the `imagePullPolicy: Never` from the deployments. Then you can use the same `kubectl` commands
as described in the [Starting](#starting) section.

## How it works

This example uses [Akka Cluster Bootstrap](https://doc.akka.io/docs/akka-management/current/bootstrap/index.html)
to initialize the cluster, using the [Kubernetes API discovery mechanism](https://doc.akka.io/docs/akka-management/current/discovery/index.html#discovery-method-kubernetes-api) 
to find peer nodes.
