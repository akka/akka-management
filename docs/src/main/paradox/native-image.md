# Building Native Images

Building native images with Akka Management is supported out of the box for the following modules:

 * akka-management
 * akka-management-cluster-bootstrap
 * akka-management-cluster-http
 * akka-discovery-kubernetes-api
 * akka-lease-kubernetes
 * akka-rolling-update-kubernetes

Other modules can likely be used but will require figuring out and adding additional native-image metadata.

For details about building native images with Akka in general, see the @extref[Akka Documentation](akka:additional/native-image.html).
