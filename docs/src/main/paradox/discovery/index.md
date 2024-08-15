# Akka Discovery Methods

As of version `1.0.0` of Akka Management @extref:[Akka Discovery](akka:discovery/index.html)
has become a core Akka module. Older versions of Service Discovery from Akka Management are not compatible with the 
Akka Discovery module in Akka.

Akka Management contains methods for:

 * @ref[Kubernetes](kubernetes.md)
 * @ref[Marathon](marathon.md)
 * @ref[AWS](aws.md)
 * @ref[Azure](azure.md)
 
The @ref[Kubernetes](kubernetes.md) and @extref:[Akka Discovery DNS](akka:discovery/index.html#discovery-method-dns)
methods are known to be well used and tested. The others are community contributions that are not tested as
part of the build and release process.
 
@@@ index

  - [Kubernetes](kubernetes.md)
  - [Marathon](marathon.md)
  - [AWS](aws.md)
  - [Azure](azure.md)
  - [Consul (discontinued)](consul.md)
  
@@@
