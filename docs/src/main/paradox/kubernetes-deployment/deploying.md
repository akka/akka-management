# Deploying

Having configured a DeploymentSpec, Role, and RoleBinding they can be created with:

```
kubectl apply -f kubernetes/akka-cluster.yaml
```

Where akka `kubernetes/akka-cluster.yaml` is location of the Kubernetes resources files in the samples.

@@@note
If you haven't been creating the files as you go for the guide, but rather are relying on the existing 
files distributed with the sample app, make sure you have performed the following easy to miss steps:

* The $spec.path$ `RoleBinding` spec @ref[needs to have the namespace updated](forming-a-cluster.md#role-based-access-control) for the user 
  name if you are not using the `appka-1` namespace.
@@@

Immediately after running this, you should see the three pods when you run `kubectl get pods`:

@@@vars
```
akka-sample-cluster-kubernetes-756894d68d-9sltd         0/1       Running   0          9s
akka-sample-cluster-kubernetes-756894d68d-bccdv         0/1       Running   0          9s
akka-sample-cluster-kubernetes-756894d68d-d8h5j         0/1       Running   0          9s
```
@@@

## Understanding bootstrap logs

Let's take a look at their logs as they go through the cluster bootstrap process. The logs can be very useful for diagnosing cluster startup problems, 
so understanding what messages will be logged when, and what information they should contain, can greatly help in achieving that.

To view the logs, run:

```sh
kubectl logs -f deployment/appka
```

This shows the logs for the first container in the deployment.

You can also pass the name of a specific pod from the list returned by `kubectl get pods` to see the logs for that pod 
(the actual name is random so you'll need to copy from your output, not use the name in this guide):

```sh
kubectl log -f pods/akka-sample-cluster-kubernetes-756894d68d-9sltd
```

By default, the logging in the application during startup is reasonably noisy. You may wish to set the logging to a higher threshold (eg warn) if you wish to 
make the logs quieter, but for now it will help us to understand what is happening. Below is a curated selection of log messages, with much of the extraneous information (such as timestamps, threads, logger names) removed. Also, you will see a lot of info messages when features that depend on the cluster start up, but a cluster has not yet been formed. Typically these messages come from cluster singleton or shard region actors. These messages will stop soon after the cluster is formed, and can be safely ignored.

@@@vars
```

1  [INFO] [akka.remote.artery.tcp.ArteryTcpTransport]  - Remoting started with transport [Artery tcp]; listening on address [akka://Appka@172.17.0.6:25520] with UID [4609278524397890522] MDC: {akkaAddress=akka://Appka@172.17.0.6:25520, sourceThread=main, akkaSource=ArteryTcpTransport(akka://Appka), sourceActorSystem=Appka, akkaTimestamp=10:04:51.188UTC}
   [INFO] [akka.cluster.Cluster] [] [Appka-akka.actor.default-dispatcher-3] - Cluster Node [akka://Appka@172.17.0.6:25520] - Starting up, Akka version [2.6.5] ... MDC: {akkaAddress=akka://Appka@172.17.0.6:25520, sourceThread=main, akkaSource=Cluster(akka://Appka), sourceActorSystem=Appka, akkaTimestamp=10:04:51.240UTC}
   [INFO] [akka.cluster.Cluster] [] [Appka-akka.actor.default-dispatcher-6] - Cluster Node [akka://Appka@172.17.0.6:25520] - No seed-nodes configured, manual cluster join required, see https://doc.akka.io/docs/akka/current/typed/cluster.html#joining MDC: {akkaAddress=akka://Appka@172.17.0.6:25520, sourceThread=Appka-akka.actor.internal-dispatcher-5, akkaSource=Cluster(akka://Appka), sourceActorSystem=Appka, akkaTimestamp=10:04:51.619UTC}
   [INFO] [akka.cluster.bootstrap.demo.DemoApp] [] [Appka-akka.actor.default-dispatcher-6] - Started [akka://Appka], cluster.selfAddress = akka://Appka@172.17.0.6:25520) MDC: {akkaAddress=akka://Appka@172.17.0.6:25520, akkaSource=akka://Appka/user, sourceActorSystem=Appka}

2a [INFO] [akka.management.internal.HealthChecksImpl] [] [Appka-akka.actor.default-dispatcher-3] - Loading readiness checks [(cluster-membership,akka.management.cluster.scaladsl.ClusterMembershipCheck), (example-ready,akka.cluster.bootstrap.demo.DemoHealthCheck)] MDC: {akkaAddress=akka://Appka@172.17.0.6:25520, sourceThread=Appka-akka.actor.default-dispatcher-6, akkaSource=HealthChecksImpl(akka://Appka), sourceActorSystem=Appka, akkaTimestamp=10:04:53.510UTC}
   [INFO] [akka.management.internal.HealthChecksImpl] [] [Appka-akka.actor.default-dispatcher-3] - Loading liveness checks [] MDC: {akkaAddress=akka://Appka@172.17.0.6:25520, sourceThread=Appka-akka.actor.default-dispatcher-6, akkaSource=HealthChecksImpl(akka://Appka), sourceActorSystem=Appka, akkaTimestamp=10:04:53.510UTC}
   [INFO] [akka.management.scaladsl.AkkaManagement] [] [Appka-akka.actor.default-dispatcher-13] - Binding Akka Management (HTTP) endpoint to: 172.17.0.6:8558 MDC: {akkaAddress=akka://Appka@172.17.0.6:25520, sourceThread=Appka-akka.actor.default-dispatcher-6, akkaSource=AkkaManagement(akka://Appka), sourceActorSystem=Appka, akkaTimestamp=10:04:53.534UTC}

2b [INFO] [akka.management.scaladsl.AkkaManagement] [] [Appka-akka.actor.default-dispatcher-3] - Including HTTP management routes for ClusterHttpManagementRouteProvider MDC: {akkaAddress=akka://Appka@172.17.0.6:25520, sourceThread=Appka-akka.actor.default-dispatcher-6, akkaSource=AkkaManagement(akka://Appka), sourceActorSystem=Appka, akkaTimestamp=10:04:53.546UTC}
   [INFO] [akka.management.scaladsl.AkkaManagement] [] [Appka-akka.actor.default-dispatcher-3] - Including HTTP management routes for ClusterBootstrap MDC: {akkaAddress=akka://Appka@172.17.0.6:25520, sourceThread=Appka-akka.actor.default-dispatcher-6, akkaSource=AkkaManagement(akka://Appka), sourceActorSystem=Appka, akkaTimestamp=10:04:53.620UTC}
   [INFO] [akka.management.cluster.bootstrap.ClusterBootstrap] [] [Appka-akka.actor.default-dispatcher-3] - Using self contact point address: http://172.17.0.6:8558 MDC: {akkaAddress=akka://Appka@172.17.0.6:25520, sourceThread=Appka-akka.actor.default-dispatcher-6, akkaSource=ClusterBootstrap(akka://Appka), sourceActorSystem=Appka, akkaTimestamp=10:04:53.624UTC}
   [INFO] [akka.management.scaladsl.AkkaManagement] [] [Appka-akka.actor.default-dispatcher-3] - Including HTTP management routes for HealthCheckRoutes MDC: {akkaAddress=akka://Appka@172.17.0.6:25520, sourceThread=Appka-akka.actor.default-dispatcher-6, akkaSource=AkkaManagement(akka://Appka), sourceActorSystem=Appka, akkaTimestamp=10:04:53.651UTC}
   [INFO] [akka.management.scaladsl.AkkaManagement] [akkaManagementBound] [Appka-akka.actor.default-dispatcher-3] - Bound Akka Management (HTTP) endpoint to: 172.17.0.6:8558 MDC: {akkaAddress=akka://Appka@172.17.0.6:25520, akkaHttpAddress=172.17.0.6:8558, sourceThread=Appka-akka.actor.default-dispatcher-13, akkaSource=AkkaManagement(akka://Appka), sourceActorSystem=Appka, akkaTimestamp=10:04:53.692UTC}

3  [INFO] [akka.management.cluster.bootstrap.ClusterBootstrap] [] [Appka-akka.actor.default-dispatcher-3] - Initiating bootstrap procedure using kubernetes-api method... MDC: {akkaAddress=akka://Appka@172.17.0.6:25520, sourceThread=Appka-akka.actor.default-dispatcher-6, akkaSource=ClusterBootstrap(akka://Appka), sourceActorSystem=Appka, akkaTimestamp=10:04:53.671UTC}
   [INFO] [akka.management.cluster.bootstrap.ClusterBootstrap] [] [Appka-akka.actor.default-dispatcher-3] - Bootstrap using `akka.discovery` method: kubernetes-api MDC: {akkaAddress=akka://Appka@172.17.0.6:25520, sourceThread=Appka-akka.actor.default-dispatcher-6, akkaSource=ClusterBootstrap(akka://Appka), sourceActorSystem=Appka, akkaTimestamp=10:04:53.673UTC}

4  [INFO] [akka.management.cluster.bootstrap.internal.BootstrapCoordinator] [akkaBootstrapInit] [Appka-akka.actor.default-dispatcher-3] - Locating service members. Using discovery [akka.discovery.kubernetes.KubernetesApiServiceDiscovery], join decider [akka.management.cluster.bootstrap.LowestAddressJoinDecider], scheme [http] MDC: {akkaAddress=akka://Appka@172.17.0.6:25520, sourceThread=Appka-akka.actor.default-dispatcher-13, akkaSource=akka://Appka/system/bootstrapCoordinator, sourceActorSystem=Appka, akkaTimestamp=10:04:53.843UTC}
   [INFO] [akka.management.cluster.bootstrap.internal.BootstrapCoordinator] [] [Appka-akka.actor.default-dispatcher-3] - Looking up [Lookup(appka,None,Some(tcp))] MDC: {akkaAddress=akka://Appka@172.17.0.6:25520, sourceThread=Appka-akka.actor.default-dispatcher-13, akkaSource=akka://Appka/system/bootstrapCoordinator, sourceActorSystem=Appka, akkaTimestamp=10:04:53.844UTC}
   [INFO] [akka.discovery.kubernetes.KubernetesApiServiceDiscovery] [] [Appka-akka.actor.default-dispatcher-3] - Querying for pods with label selector: [app=appka]. Namespace: [appka-1]. Port: [None] MDC: {akkaAddress=akka://Appka@172.17.0.6:25520, sourceThread=Appka-akka.actor.default-dispatcher-13, akkaSource=KubernetesApiServiceDiscovery(akka://Appka), sourceActorSystem=Appka, akkaTimestamp=10:04:53.844UTC}

5  [INFO] [akka.management.cluster.bootstrap.internal.BootstrapCoordinator] [akkaBootstrapResolved] [Appka-akka.actor.default-dispatcher-3] - Located service members based on: [Lookup(appka,None,Some(tcp))]: [ResolvedTarget(172-17-0-6.appka-1.pod.cluster.local,None,Some(/172.17.0.6)), ResolvedTarget(172-17-0-7.appka-1.pod.cluster.local,None,Some(/172.17.0.7)), ResolvedTarget(172-17-0-5.appka-1.pod.cluster.local,None,Some(/172.17.0.5))], filtered to [172-17-0-5.appka-1.pod.cluster.local:0, 172-17-0-6.appka-1.pod.cluster.local:0, 172-17-0-7.appka-1.pod.cluster.local:0] MDC: {akkaAddress=akka://Appka@172.17.0.6:25520, akkaContactPoints=172-17-0-5.appka-1.pod.cluster.local:0, 172-17-0-6.appka-1.pod.cluster.local:0, 172-17-0-7.appka-1.pod.cluster.local:0, sourceThread=Appka-akka.actor.default-dispatcher-13, akkaSource=akka://Appka/system/bootstrapCoordinator, sourceActorSystem=Appka, akkaTimestamp=10:04:54.919UTC}

6  [INFO] [akka.management.cluster.bootstrap.internal.BootstrapCoordinator] [akkaBootstrapSeedNodes] [Appka-akka.actor.default-dispatcher-20] - Contact point [akka://Appka@172.17.0.5:25520] returned [1] seed-nodes [akka://Appka@172.17.0.5:25520] MDC: {akkaAddress=akka://Appka@172.17.0.6:25520, sourceThread=Appka-akka.actor.default-dispatcher-11, akkaSource=akka://Appka/system/bootstrapCoordinator, sourceActorSystem=Appka, akkaTimestamp=10:05:01.306UTC, akkaSeedNodes=akka://Appka@172.17.0.5:25520}
   [INFO] [akka.management.cluster.bootstrap.internal.BootstrapCoordinator] [akkaBootstrapJoin] [Appka-akka.actor.default-dispatcher-20] - Joining [akka://Appka@172.17.0.6:25520] to existing cluster [akka://Appka@172.17.0.5:25520] MDC: {akkaAddress=akka://Appka@172.17.0.6:25520, sourceThread=Appka-akka.actor.default-dispatcher-11, akkaSource=akka://Appka/system/bootstrapCoordinator, sourceActorSystem=Appka, akkaTimestamp=10:05:01.309UTC, akkaSeedNodes=akka://Appka@172.17.0.5:25520}

7  [INFO] [akka.cluster.Cluster] [] [Appka-akka.actor.default-dispatcher-11] - Cluster Node [akka://Appka@172.17.0.6:25520] - Welcome from [akka://Appka@172.17.0.5:25520] MDC: {akkaAddress=akka://Appka@172.17.0.6:25520, sourceThread=Appka-akka.actor.internal-dispatcher-2, akkaSource=Cluster(akka://Appka), sourceActorSystem=Appka, akkaTimestamp=10:05:01.918UTC}
   [INFO] [akka.cluster.bootstrap.demo.DemoApp] [] [Appka-akka.actor.default-dispatcher-19] - MemberEvent: MemberUp(Member(address = akka://Appka@172.17.0.5:25520, status = Up)) MDC: {akkaAddress=akka://Appka@172.17.0.6:25520, akkaSource=akka://Appka/user, sourceActorSystem=Appka}
   [INFO] [akka.cluster.bootstrap.demo.DemoApp] [] [Appka-akka.actor.default-dispatcher-19] - MemberEvent: MemberJoined(Member(address = akka://Appka@172.17.0.6:25520, status = Joining)) MDC: {akkaAddress=akka://Appka@172.17.0.6:25520, akkaSource=akka://Appka/user, sourceActorSystem=Appka}
   [INFO] [akka.cluster.bootstrap.demo.DemoApp] [] [Appka-akka.actor.default-dispatcher-19] - MemberEvent: MemberJoined(Member(address = akka://Appka@172.17.0.7:25520, status = Joining)) MDC: {akkaAddress=akka://Appka@172.17.0.6:25520, akkaSource=akka://Appka/user, sourceActorSystem=Appka}
   [INFO] [akka.cluster.bootstrap.demo.DemoApp] [] [Appka-akka.actor.default-dispatcher-19] - MemberEvent: MemberUp(Member(address = akka://Appka@172.17.0.6:25520, status = Up)) MDC: {akkaAddress=akka://Appka@172.17.0.6:25520, akkaSource=akka://Appka/user, sourceActorSystem=Appka}
   [INFO] [akka.cluster.bootstrap.demo.DemoApp] [] [Appka-akka.actor.default-dispatcher-19] - MemberEvent: MemberUp(Member(address = akka://Appka@172.17.0.7:25520, status = Up)) MDC: {akkaAddress=akka://Appka@172.17.0.6:25520, akkaSource=akka://Appka/user, sourceActorSystem=Appka}
```
@@@

An explanation of these messages is as follows.

1. These are init messages, showing that remoting has started on port 25520. The IP address should be the pods IP address from which other pods can access it, while the port number should match the configured remoting number, defaulting to 25520.
2. Init messages for Akka management, once again, the IP address should be the pods IP address, while the port number should be the port number you've configured for Akka management to use, defaulting to 8558.
   Akka management is also hosting the readiness and liveness checks.
3. Now the cluster bootstrap process is starting. The service name should match your Akka system name or configured service name in cluster bootstrap, and the port should match your configured port name. In this guide we kept these as the default values.
   This and subsequent messages will be repeated many times as cluster bootstrap polls Kubernetes and the other pods to determine what pods have been started, and whether and where a cluster has been formed.
4. This is the disocvery process. The bootstarp coordinator uses the Kubernetes discovery mechanism. The label selector should be one that will return your pods, and the namespace should match your applications namespace. The namespace is picked up automatically.
5. Here the Kubernetes API has returned three services, including ourselves.
6. The pod has decided to join an existing cluster. On one node the pod will decide to form the initial cluster.
7. The node has joined and has member up events for all other nodes.

Following these messages, you may still see some messages warning that messages can't be routed, it still may take some time for cluster singletons and other cluster features to decide which pod to start up on, but before long, the logs should go quiet as the cluster is started up.

The logs above show those of a pod that wasn't the pod to start the cluster. As mentioned earlier, the default strategy that Akka Cluster Bootstrap uses when it starts and finds that there is no existing cluster is to get the pod with the lowest IP address to start the cluster. In the example above, that pod has an IP address of `172.17.0.6`, 
and ends up joining a pod with IP `172.17.0.5` as it has a lower IP.

If you look in the logs of that pod, you'll see a message like this:

```
[INFO] [akka.management.cluster.bootstrap.internal.BootstrapCoordinator] [akkaBootstrapJoinSelf] [Appka-akka.actor.default-dispatcher-19] - Initiating new cluster, self-joining [akka://Appka@172.17.0.5:25520]. Other nodes are expected to locate this cluster via continued contact-point probing. MDC: {akkaAddress=akka://Appka@172.17.0.5:25520, sourceThread=Appka-akka.actor.default-dispatcher-11, akkaSource=akka://Appka/system/bootstrapCoordinator, sourceActorSystem=Appka, akkaTimestamp=10:05:00.873UTC}
```

This message will appear after a timeout called the stable margin, which defaults to 5 seconds, at that point, the pod has seen that there have been no changes to the number of pods deployed for 5 seconds, and so given that it has the lowest IP address, it considers it safe for it to start a new cluster.

If your cluster is failing to form, carefully check over the logs for the following things:

* Make sure the right IP addresses are in use. If you see `localhost` or `127.0.0.1` used anywhere, that is generally an indication of a misconfiguration.
* Ensure that the namespace, service name, label selector, port name and protocol all match your deployment spec.
* Ensure that the port numbers match what you've configured both in the configuration files and in your deployment spec.
* Ensure that the required contact point number matches your configuration and the number of replicas you have deployed.
* Ensure that pods are successfully polling each other, looking for messages such as `Contact point [...] returned...` for outgoing polls and `Bootstrap request from ...` for incoming polls from other pods.

## Deploying to minikube

To deploy the samples to minikube:

* Setup your local docker environment to point to minikube: `eval $(minikube -p minikube docker-env)`
* Deploy the image: `sbt docker:publishLocal`
* The deployment specs in the samples contain `imagePullPolicy: Never` to prevent Kubernetes trying to download the image from an external registry
* Create the namespace and deployment:

```
kubectl apply -f kubernetes/namespace.json
kubectl config set-context --current --namespace=appka-1
kubectl apply -f kubernetes/akka-cluster.yml
```
   
Finally, create a service so that you can then test [http://127.0.0.1:8080](http://127.0.0.1:8080)
for 'hello world':



    kubectl expose deployment appka --type=LoadBalancer --name=appka-service

You can inspect the Akka Cluster membership status with the [Cluster HTTP Management](https://doc.akka.io/docs/akka-management/current/cluster-http-management.html).

    curl http://127.0.0.1:8558/cluster/members/

