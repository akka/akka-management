# Bootstrap recipes 

A set of bootstrap demonstration projects can be found in [bootstrap-demo](https://github.com/akka/akka-management/tree/master/bootstrap-demo) folder of this project. Currently there are projects for:

* AWS API
* Mesos using DNS
* Kubernetes using the API server
* Kubernetes using DNS
* Marathon

## Local 

To run Bootstrap locally without any dependencies such as DNS or Kubernetes see the @ref[`local` example](./local-config.md)

## Kubernetes

For Akka Cluster in Kubernetes either `akka-dns` or `kubernetes-api` service discovery can be used:

* @ref[Kubernetes using `akka-dns` discovery](./kubernetes.md)
* @ref[Kubernetes using `kubernetes-api` discovery](./kubernetes-api.md)

DNS has the benefit that it is agnostic of Kubernetes so does not require pods be able to communicate with the API
server. However it requires a headless service that supports the `publishNotReadyAddresses` feature.
