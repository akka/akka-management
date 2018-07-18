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

/**
 * Implement to provide basic service discovery mechanism.
 *
 * "Simple" because it's only the basic "lookup" methods, no ability to "keep monitoring a namespace for changes" etc.
 */
@ApiMayChange
object SimpleServiceDiscovery {

  // TODO for use cases like aggregate it might be worth making this an ADT with a NoTargets case
  /** Result of a successful resolve request */
  final case class Resolved(serviceName: String, addresses: immutable.Seq[ResolvedTarget])
      extends DeadLetterSuppression {

    def getAddresses: java.util.List[ResolvedTarget] = {
      import scala.collection.JavaConverters._
      addresses.asJava
    }
  }

  object ResolvedTarget {
    implicit val addressOrdering: Ordering[ResolvedTarget] = Ordering.fromLessThan[ResolvedTarget] { (a, b) ⇒
      if (a eq b) false
      else if (a.host != b.host) a.host.compareTo(b.host) < 0
      else if (a.port != b.port) a.port.getOrElse(0) < b.port.getOrElse(0)
      else false
    }

    private val IPv4 = """^((?:[0-9]{1,3}\.){3}[0-9]{1,3})$""".r

    def apply(host: String, port: Option[Int]): ResolvedTarget = {
      val address = host match {
        case IPv4(_) => Some(InetAddress.getByName(host))
        case _ => None
      }
      new ResolvedTarget(host, port, address)
    }
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
case class Lookup(serviceName: String, portName: Option[String] = None, protocol: Option[String] = None) {

  /**
   * Which port for a service e.g. Akka remoting or HTTP.
   * Maps to "service" for an SRV records.
   */
  def withPortName(value: String): Lookup = copy(
    portName = Some(value)
  )

  /**
   * Which protocol e.g. TCP or UDP.
   * Maps to "protocol" for SRV records.
   */
  def withProtocol(value: String): Lookup = copy(
    protocol = Some(value)
  )
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
  def lookup(name: String, resolveTimeout: FiniteDuration): Future[Resolved] =
    lookup(Lookup(name), resolveTimeout)

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
   * @param name           A name, see discovery-mechanism's docs for how this is interpreted
   * @param resolveTimeout Timeout. Up to the discovery-mechanism to adhere to his
   */
  def lookup(name: String, resolveTimeout: java.time.Duration): CompletionStage[Resolved] =
    lookup(Lookup(name), resolveTimeout)

}
