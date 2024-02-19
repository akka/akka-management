# Building Native Images

Building native images with Akka HTTP is supported out of the box for the following modules:

 * akka-management
 * akka-management-cluster-bootstrap
 * akka-management-cluster-http
 * akka-management-discovery-kubernetes-api
 * akka-lease-kubernetes
 * akka-rolling-upgrade-kubernetes

Other modules can likely be used but will require figuring out and adding additional native-image metadata.

For details about building native images with Akka in general, see the @extref[Akka Documentation](akka:additional/native-image.html).