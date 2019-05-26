# Kubernetes via DNS

An example project that can be deployed to kubernetes via `minikube` is in `integration-test/kubernetes-dns`.

The project shows how to:

* Use Akka Bootstrap with `akka-dns` with cluster formation via DNS SRV records
* Use a headless service for internal and Akka management/bootstrap so that readiness probes for prod traffic don't interfere with bootstrap
    * Note that this requires the use of the `publishNotReadyAddresses`, which replaces the `service.alpha.kubernetes.io/tolerate-unready-endpoints: "true"` annotation , so bootstrap can see the pods before they are ready. Check your Kubernetes environment supports this feature
* If required use a separate service and/or ingress for user-facing endpoints, for example @extref:[HTTP](akka-http:) or @extref:[gRPC](akka-grpc:)

### Internal headless service for bootstrap

For Akka Cluster / Management use a headless service. This allows the solution to not be coupled to k8s as well
as there is no use case for load balancing across management/remoting ports.
Set endpoints to be published before readiness checks pass as these endpoints are required to bootstrap the Cluster
and make the application ready.

@@snip [akka-cluster.yml](/integration-test/kubernetes-dns/kubernetes/akka-cluster.yml)  { #headless }

Note there are currently two ways to specify that addresses should be published if not ready, the initial way via an annotation
`service.alpha.kubernetes.io/tolerate-unready-endpoints` and via the new officially supported way as the property `publishNotReadyAddresses`.
Set both as depending on your DNS solution it may have not migrated from the annotation to the property.

This will result in SRV records being published for the service that contain the nodes that are not ready. This allows
bootstrap to find them and form the cluster thus making them ready.

Then to configure your application:

@@snip [application.conf](/integration-test/kubernetes-dns/src/main/resources/application.conf) { #management }

The same configuration will work for any environment that has an SRV record for your Akka Clustered application.

For more details on how to configure the Kubernetes deployment see @ref:[recipes](recipes.md).

