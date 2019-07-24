# Kubernetes API

The Kubernetes API can be used to discover peers and form an Akka Cluster. The `kubernetes-api`
mechanism queries the Kubernetes API server to find pods with a given label. A Kubernetes service isn't required
for the cluster bootstrap but may be used for external access to the application.

The following Kubernetes resources are created:

* Deployment: For creating the Akka pods
* Role and RoleBinding to give the pods access to the API server

An example deployment (used for integration testing):

@@snip [akka-cluster.yml](/integration-test/kubernetes-api/kubernetes/akka-cluster.yml) { #deployment }

An example role and rolebinding to allow the nodes to query the Kubernetes API server:

@@snip [akka-cluster.yml](/integration-test/kubernetes-api/kubernetes/akka-cluster.yml) { #rbac }

The User name includes the namespace, this will need updated for your namespace.

The following configuration is required:

* Set `akka.management.cluster.bootstrap.contact-point-discovery.discovery-method` to `kubernetes-api`
* Set `akka.discovery.kubernetes-api.pod-label-selector` to a label selector that will match the Akka pods e.g. `app=%s`

@@snip [akka-cluster.yml](/integration-test/kubernetes-api/src/main/resources/application.conf) { #discovery-config }

The lookup needs to know which namespace to look in. By default, this will be detected by reading the namespace
from the service account secret, in `/var/run/secrets/kubernetes.io/serviceaccount/namespace`, but can be overridden by
setting `akka.discovery.kubernetes-api.pod-namespace`.

For more details on how to configure the Kubernetes deployment see @ref:[recipes](recipes.md).

