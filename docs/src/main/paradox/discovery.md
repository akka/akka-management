<a id="discovery"></a>
# Service Discovery

Akka Discovery provides a simple interface around various ways of locating services, such as DNS
or using configuration or key-value stores like zookeeper, consul etc.

## What is Service Discovery

Akka's Service Discovery talks specifically about discovering hosts and ports that relate to some
logical name of a service.

If you're looking for a way to discover Actors in a Cluster, you may want to look at the Receptionist
pattern from Akka Typed instead. Since it provides a more fine-tuned towards Actors mechanism of
doing the discovery.

# Implementations

TODO: Discussion about DNS vs other key-value stores.

## Akka DNS Discovery

Uses `A` records to discover a service's address (or addresses).

## How to contribute implementations

Contributions to alternative data-stores or service-discovery APIs built-in to specific cloud systems
are happily accepted. Please open an issue on the github issue tracker to discuss the integration
you'd like to contribute first.
