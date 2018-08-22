## Examples

### Kubernetes example

In Kubernetes, one would deploy an Akka Cluster as a single [Headless Service](https://kubernetes.io/docs/concepts/services-networking/service/#headless-services).

An example application using docker and prepared to be deployed to kubernetes is provided in Akka Management's github repository 
as sub-project [bootstrap-joining-demo](https://github.com/akka/akka-management/tree/master/bootstrap-joining-demo/kubernetes-api).

Rather than configuring the Dockerfile directly, we used the [sbt-native-packager](http://sbt-native-packager.readthedocs.io/en/stable/) 
to package the application as docker container. See the `build.sbt` file for more details, and the `kubernetes/akka-cluster.yml` 
file for the service configuration, which is:

@@snip [akka-cluster.yml](../../../../../bootstrap-joining-demo/kubernetes-api/kubernetes/akka-cluster.yml) 

You run the example using [minikube](https://github.com/kubernetes/minikube) (or a real kubernetes system),
which you can do by typing:

```
# 1) make sure you have installed `minikube` (see link above)
```

```
# 2) make sure minikube is running
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

```
# 3) make sure your shell is configured to target minikube cluster
$ eval $(minikube docker-env) 
```

```
# 4) Publish the application docker image locally:
$ sbt shell
...
> project bootstrap-joining-demo-kubernetes-api
... 
> docker:publishLocal 
...
[info] Successfully tagged ktoso/akka-management-bootstrap-joining-demo-kubernetes-api:1.3.3.7
[info] Built image ktoso/akka-management-bootstrap-joining-demo-kubernetes-api:1.3.3.7
[success] Total time: 25 s, completed Dec 8, 2017 7:47:05 PM
```

Once the image is published, you can deploy it onto the kubernetes cluster by calling:

@@snip [kube-create.sh](../../../../../bootstrap-joining-demo/kubernetes-api/kube-create.sh) 

This will create and start running a number of Pods hosting the application. The application nodes will proceed with 
forming the cluster using the DNS bootstrap method. In order to observe the logs during the cluster formation you can 
pick one of the pods and simply issue the kubectl logs command on it, like this:

```
$ POD=$(kubectl get pods | grep appka | grep Running | head -n1 | awk '{ print $1 }'); echo $POD
appka-6bfdf47ff6-l7cpb

$ kubectl logs $POD -f
...
[INFO] [12/08/2017 10:57:52.678] [main] [akka.remote.Remoting] Starting remoting
...
[INFO] [12/08/2017 10:57:53.597] [main] [akka.remote.Remoting] Remoting started; listening on addresses :[akka.tcp://Appka@172.17.0.2:2552]
...
[INFO] [12/08/2017 10:58:00.558] [main] [ClusterHttpManagement(akka://Appka)] Bound akka-management HTTP endpoint to: 172.17.0.2:8558
[INFO] [12/08/2017 10:58:00.559] [main] [ClusterBootstrap(akka://Appka)] Initiating bootstrap procedure using akka.discovery.akka-dns method...
...
[INFO] [12/08/2017 10:58:04.747] [Appka-akka.actor.default-dispatcher-2] [akka.tcp://Appka@172.17.0.2:2552/system/bootstrapCoordinator] Initiating new cluster, self-joining [akka.tcp://Appka@172.17.0.2:2552], as this node has the LOWEST address out of: [List(ResolvedTarget(172.17.0.2,None), ResolvedTarget(172.17.0.6,None), ResolvedTarget(172.17.0.4,None), ResolvedTarget(172.17.0.3,None))]! Other nodes are expected to locate this cluster via continued contact-point probing.
[INFO] [12/08/2017 10:58:04.796] [Appka-akka.actor.default-dispatcher-15] [akka.cluster.Cluster(akka://Appka)] Cluster Node [akka.tcp://Appka@172.17.0.2:2552] - Node [akka.tcp://Appka@172.17.0.2:2552] is JOINING, roles [dc-default]
[INFO] [12/08/2017 10:58:04.894] [Appka-akka.actor.default-dispatcher-15] [akka.cluster.Cluster(akka://Appka)] Cluster Node [akka.tcp://Appka@172.17.0.2:2552] - Leader is moving node [akka.tcp://Appka@172.17.0.2:2552] to [Up]
[INFO] [12/08/2017 10:58:04.920] [Appka-akka.actor.default-dispatcher-16] [akka.tcp://Appka@172.17.0.2:2552/user/$a] Cluster akka.tcp://Appka@172.17.0.2:2552 >>> MemberUp(Member(address = akka.tcp://Appka@172.17.0.2:2552, status = Up))
...
```

You can also see the pods in the dashboard:

```
$ minikube dashboard
```

To finally stop it you use:

```
$ minikube stop
```

Which concludes the short demo of cluster bootstrap in kubernetes using the DNS discovery mechanism.