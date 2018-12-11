# Kubernetes API

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

The lookup needs to know which namespace to look in. By default, this will be detected by reading the namespace
from the service account secret, in `/var/run/secrets/kubernetes.io/serviceaccount/namespace`, but can be overridden by
setting `akka.discovery.kubernetes-api.pod-namespace`.

For more details on how to configure the Kubernetes deployment see @ref:[recipes](recipes.md).

