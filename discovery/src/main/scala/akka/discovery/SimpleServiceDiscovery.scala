/*
 * Copyright (C) 2017 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.discovery

import java.net.InetAddress
import java.util.Optional
import java.util.concurrent.CompletionStage
import java.util.concurrent.TimeUnit

import akka.actor.DeadLetterSuppression
import akka.annotation.ApiMayChange

import scala.collection.immutable
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.util.Try

/**
 * Implement to provide basic service discovery mechanism.
 *
 * "Simple" because it's only the basic "lookup" methods, no ability to "keep monitoring a namespace for changes" etc.
 */
@ApiMayChange
object SimpleServiceDiscovery {

  /** Result of a successful resolve request */
  final case class Resolved(serviceName: String, addresses: immutable.Seq[ResolvedTarget])
      extends DeadLetterSuppression {

    def getAddresses: java.util.List[ResolvedTarget] = {
      import scala.collection.JavaConverters._
      addresses.asJava
    }
  }

  object ResolvedTarget {
    // Simply compare the bytes of the address.
    // This may not work in exotic cases such as IPv4 addresses encoded as IPv6 addresses.
    private implicit val inetAddressOrdering: Ordering[InetAddress] =
      Ordering.by[InetAddress, Iterable[Byte]](_.getAddress)

    implicit val addressOrdering: Ordering[ResolvedTarget] = Ordering.by { t =>
      (t.address, t.host, t.port)
    }

    def apply(host: String, port: Option[Int]): ResolvedTarget =
      ResolvedTarget(host, port, Try(InetAddress.getByName(host)).toOption)
  }

  /**
   * Resolved target host, with optional port and the IP address.
   * @param host the hostname or the IP address of the target
   * @param port optional port number
   * @param address optional IP address of the target. This is used during cluster bootstap when available.
   */
  final case class ResolvedTarget(
      host: String,
      port: Option[Int],
      address: Option[InetAddress]
  ) {
    def getPort: Optional[Int] = {
      import scala.compat.java8.OptionConverters._
      port.asJava
    }

    def getAddress: Optional[InetAddress] = {
      import scala.compat.java8.OptionConverters._
      address.asJava
    }
  }

}

/**
 * A service lookup. It is up to each mechanism to decide
 * what to do with the optional portName and protocol fields.
 * For example `portName` could be used to distinguish between
 * Akka remoting ports and HTTP ports.
 *
 */
@ApiMayChange
case class Lookup(serviceName: String, portName: Option[String], protocol: Option[String]) {

  /**
   * Which port for a service e.g. Akka remoting or HTTP.
   * Maps to "service" for an SRV records.
   */
  def withPortName(value: String): Lookup = copy(portName = Some(value))

  /**
   * Which protocol e.g. TCP or UDP.
   * Maps to "protocol" for SRV records.
   */
  def withProtocol(value: String): Lookup = copy(protocol = Some(value))
}

case object Lookup {

  /**
   * Create a simple service Lookup with only a serviceName.
   * Use withPortName and withProtocol to provide optional portName
   * and protocol
   */
  def apply(serviceName: String): Lookup = new Lookup(serviceName, None, None)

  /**
   * Java API
   *
   * Create a simple service Lookup with only a serviceName.
   * Use withPortName and withProtocol to provide optional portName
   * and protocol
   */
  def create(serviceName: String): Lookup = new Lookup(serviceName, None, None)

  private val SrvQuery = """^_(.+?)\._(.+?)\.(.+?)$""".r

  private val DomainName = "^((?!-)[A-Za-z0-9-]{1,63}(?<!-)\\.)+[A-Za-z]{2,6}$".r

  /**
   * Create a service Lookup from a string with format:
   * _portName._protocol.serviceName.
   * (as specified by https://www.ietf.org/rfc/rfc2782.txt)
   *
   * If the passed string conforms with this format, a SRV Lookup is returned.
   *
   * The string is parsed and dismembered to build a Lookup as following:
   * Lookup(serviceName).withPortName(portName).withProtocol(protocol)
   *
   * The serviceName part must be a valid domain name.
   *
   * If the string doesn't not conform with the SRV format, a simple A/AAAA Lookup is returned using the whole string as service name.
   *
   * @throws IllegalArgumentException if the service name extracted from the SRV string is not a valid domain name.
   */
  def fromString(str: String): Lookup = {

    def fromDomainName(name: String): Lookup = {
      if (validDomainName(name))
        Lookup(name)
      else
        throw new IllegalArgumentException(s"Illegal domain name lookup: $name")
    }

    str match {
      case SrvQuery(portName, protocol, serviceName) =>
        fromDomainName(serviceName).withPortName(portName).withProtocol(protocol)

      case _ => Lookup(str)
    }
  }

  /**
   * Returns true if passed string conforms with SRV format. Otherwise returns false.
   */
  def isValidSrv(srv: String): Boolean =
    srv match {
      case SrvQuery(_, _, serviceName) if validDomainName(serviceName) => true
      case _ => false
    }

  private def validDomainName(name: String): Boolean =
    DomainName.pattern.asPredicate().test(name)

}

/**
 * Implement to provide basic service discovery mechanism.
 *
 * "Simple" because it's only the basic "lookup" methods, no ability to "keep monitoring a namespace for changes" etc.
 */
@ApiMayChange
abstract class SimpleServiceDiscovery {

  import SimpleServiceDiscovery._

  /**
   * Scala API: Perform lookup using underlying discovery implementation.
   *
   * @param lookup       A service discovery lookup.
   * @param resolveTimeout Timeout. Up to the discovery-mechanism to adhere to his
   */
  def lookup(lookup: Lookup, resolveTimeout: FiniteDuration): Future[Resolved]

  /**
   * Scala API: Perform lookup using underlying discovery implementation.
   *
   * Convenience for when only a name is required.
   */
  def lookup(serviceName: String, resolveTimeout: FiniteDuration): Future[Resolved] =
    lookup(Lookup(serviceName), resolveTimeout)

  /**
   * Java API: Perform basic lookup using underlying discovery implementation.
   *
   * While the implementation may provide other settings and ways to configure timeouts,
   * the passed `resolveTimeout` should never be exceeded, as it signals the application's
   * eagerness to wait for a result for this specific lookup.
   *
   * The returned future SHOULD be failed once resolveTimeout has passed.
   *
   */
  def lookup(query: Lookup, resolveTimeout: java.time.Duration): CompletionStage[Resolved] = {
    import scala.compat.java8.FutureConverters._
    lookup(query, FiniteDuration(resolveTimeout.toMillis, TimeUnit.MILLISECONDS)).toJava
  }

  /**
   * Java API
   *
   * @param serviceName           A name, see discovery-mechanism's docs for how this is interpreted
   * @param resolveTimeout Timeout. Up to the discovery-mechanism to adhere to his
   */
  def lookup(serviceName: String, resolveTimeout: java.time.Duration): CompletionStage[Resolved] =
    lookup(Lookup(serviceName), resolveTimeout)

}
