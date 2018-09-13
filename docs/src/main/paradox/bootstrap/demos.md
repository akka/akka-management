# Bootstrap recipes 

@@@ index

* [dns](./dns.md)
* [local-config](./local-config.md)
* [kuberntes-dns](./kubernetes.md)
* [kuberntes-api](./kubernetes-api.md)

@@@

## Local 

To run Bootstrap locally without any dependencies such as DNS or Kubernetes see the @ref[`local` example](local-config.md)

## Kubernetes

For Akka Cluster in Kubernetes either `akka-dns` or `kubernetes-api` can be used:

* @ref[Kubernetes using `akka-dns` discovery](kubernetes.md)
* @ref[Kubernetes using `kubernetes-api` discovery](kubernetes-api.md)

