# Akka Management Integration Tests

A set of projects that test Akka Bootstrap along with other Akka Management features in various environments.

Currently the following run as part of CI:

* `kubernetes-api` - uses the Kubernetes API in minikube to test bootstrap
* `kubernetes-api-dns` - uses DNS service discovery in minikube to test bootstrap
* `kubernetes-api-java` - uses the Kubernetes API in minikube to test bootstrap from a Java/Maven project
* `local` uses config service discovery to form a cluster validates health checks
