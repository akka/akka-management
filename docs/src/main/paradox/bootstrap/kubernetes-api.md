# Kubernetes API 

An example project that can be deployed to kubernetes via `minikube` is in `bootstrap-demo/kubernetes-api`.

This demo shows how to form an Akka Cluster in Kubernetes using the `kubernetes-api` discovery mechanism. The `kubernetes-api`
mechanism queries the Kubernetes API server to find pods with a given label. A Kubernetes service isn't required 
for the cluster bootstrap but may be used for external access to the application. 

The following Kubernetes resources are created:

* Deployment: For creating the Akka pods 
* Role and RoleBinding to give the pods access to the API server

@@snip [akka-cluster.yml](/bootstrap-demo/kubernetes-api/kubernetes/akka-cluster.yml) 

The following configuration is required:

* Set `akka.management.cluster.bootstrap.contact-point-discovery.discovery-method` to `akka.discovery.kubernetes-api`
* Set `akka.discovery.kubernetes-api.pod-label-selector` to a label selector that will match the Akka pods

@@snip [akka-cluster.yml](/bootstrap-demo/kubernetes-api/src/main/resources/application.conf) { #discovery-config } 

If running he example in [minikube](https://github.com/kubernetes/minikube) make sure you have installed and is running:

```
$ minikube start
Starting local Kubernetes v1.8.0 cluster...
Starting VM...
Getting VM IP address...
Moving files into cluster...
Setting up certs...
Connecting to cluster...
Setting up kubeconfig...
Starting cluster components...
Kubectl is now configured to use the cluster.
```

Make sure your shell is configured to target minikube cluster
 
```
$ eval $(minikube docker-env) 
```

For minikube publish the application docker image locally. If running this project in a real cluster you'll need to publish the image to a repository
that is accessible from your Kubernetes cluster and update the `kubernetes/akka-cluster.yml` with the new image name.

```
$ sbt shell
> project bootstrap-demo-kubernetes-api
> docker:publishLocal 
```

Once the image is published, deploy it onto the kubernetes cluster:

```
kubectl apply -f kubernetes/akka-cluster.yml
```

This will create and start running a number of Pods hosting the application. The application nodes will proceed with 
forming the cluster using the `kubernetes-api` bootstrap method. 

In order to observe the logs during the cluster formation you can 
pick one of the pods and issue the kubectl logs command on it:

```
$ POD=$(kubectl get pods | grep appka | grep Running | head -n1 | awk '{ print $1 }'); echo $POD
appka-6bfdf47ff6-l7cpb

$ kubectl logs $POD -f
```

```
[INFO] [09/11/2018 09:57:16.612] [Appka-akka.actor.default-dispatcher-4] [akka.tcp://Appka@172.17.0.14:2552/system/bootstrapCoordinator] Locating service members. Using discovery [akka.discovery.kubernetes.KubernetesApiSimpleServiceDiscovery], join decider [akka.management.cluster.bootstrap.LowestAddressJoinDecider]                                                                
[INFO] [09/11/2018 09:57:16.613] [Appka-akka.actor.default-dispatcher-4] [akka.tcp://Appka@172.17.0.14:2552/system/bootstrapCoordinator] Looking up [Lookup(appka-service.default.svc.cluster.local,Some(management),Some(tcp))]                                                                                                                                                             
[INFO] [09/11/2018 09:57:16.713] [Appka-akka.actor.default-dispatcher-19] [AkkaManagement(akka://Appka)] Bound Akka Management (HTTP) endpoint to: 172.17.0.14:8558
[INFO] [09/11/2018 09:57:16.746] [Appka-akka.actor.default-dispatcher-17] [akka.actor.ActorSystemImpl(Appka)] Querying for pods with label selector: [actorSystemName=appka]
[INFO] [09/11/2018 09:57:17.710] [Appka-akka.actor.default-dispatcher-3] [HttpClusterBootstrapRoutes(akka://Appka)] Bootstrap request from 172.17.0.15:55502: Contact Point returning 0 seed-nodes ([Set()])                                                                                                                                                                                 
[INFO] [09/11/2018 09:57:17.718] [Appka-akka.actor.default-dispatcher-17] [akka.tcp://Appka@172.17.0.14:2552/system/bootstrapCoordinator] Located service members based on: [Lookup(appka-service.default.svc.cluster.local,Some(management),Some(tcp))]: [ResolvedTarget(172-17-0-14.default.pod.cluster.local,Some(8558),Some(/172.17.0.14)), ResolvedTarget(172-17-0-15.default.pod.cluster
.local,Some(8558),Some(/172.17.0.15))]
[INFO] [09/11/2018 09:57:23.636] [Appka-akka.actor.default-dispatcher-17] [akka.tcp://Appka@172.17.0.14:2552/system/bootstrapCoordinator] Initiating new cluster, self-joining [akka.tcp://Appka@172.17.0.14:2552]. Other nodes are expected to locate this cluster via continued contact-point probing.                                                                                     
[INFO] [09/11/2018 09:57:23.650] [Appka-akka.actor.default-dispatcher-19] [akka.cluster.Cluster(akka://Appka)] Cluster Node [akka.tcp://Appka@172.17.0.14:2552] - Node [akka.tcp://Appka@172.17.0.14:2552] is JOINING itself (with roles [dc-default]) and forming new cluster                                                                                                               
[INFO] [09/11/2018 09:57:23.655] [Appka-akka.actor.default-dispatcher-19] [akka.cluster.Cluster(akka://Appka)] Cluster Node [akka.tcp://Appka@172.17.0.14:2552] - Cluster Node [akka.tcp://Appka@172.17.0.14:2552] dc [default] is the new leader                                                                                                                                            
[INFO] [09/11/2018 09:57:23.676] [Appka-akka.actor.default-dispatcher-19] [akka.cluster.Cluster(akka://Appka)] Cluster Node [akka.tcp://Appka@172.17.0.14:2552] - Leader is moving node [akka.tcp://Appka@172.17.0.14:2552] to [Up]                                                                                                                                                          
[INFO] [09/11/2018 09:57:23.680] [Appka-akka.actor.default-dispatcher-17] [akka.actor.ActorSystemImpl(Appka)] Cluster member is up!

```

The resources can be deleted from the cluster with:

```
kubectl delete services,pods,deployment -l app=appka	
kubectl delete services,pods,deployment appka-service
```
