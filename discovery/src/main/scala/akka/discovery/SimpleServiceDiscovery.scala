/*
 * Copyright (C) 2017 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.discovery

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
  }

  /** Resolved target host, with optional port and protocol spoken */
  final case class ResolvedTarget(host: String, port: Option[Int]) {
    def getPort: Optional[Int] = {
      import scala.compat.java8.OptionConverters._
      port.asJava
    }

    override def toString(): String =
      port match {
        case Some(p) => s"$host:$p"
        case None => host
      }
  }

  /**
   * Represents a service discovery lookup.
   *
   * Each mechanism should document what a Simple vs Full lookup means.
   * Implementations are free to ignore port/protocol from a Full lookup
   * but should not throw if they are set unexpectedly as this prevents the
   * same query being sent to multiple mechanisms via the aggregate service
   * discovery
   */
  @ApiMayChange
  sealed abstract class Lookup {
    def name: String
  }

  @ApiMayChange
  final case class Simple(name: String) extends Lookup

  /**
   * The meaning of each field is up to the discovery mechanism.
   */
  @ApiMayChange
  final case class Full(name: String, port: String, protocol: String) extends Lookup
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
   * @param query A simple of full query, see discovery-mechanism's docs for how this is interpreted
   * @param resolveTimeout Timeout. Up to the discovery-mechanism to adhere to his
   * @return
   */
  def lookup(query: Lookup, resolveTimeout: FiniteDuration): Future[Resolved]

  /**
   * Scala API: Perform lookup using underlying discovery implementation.
   *
   * @param name A name, see discovery-mechanism's docs for how this is interpreted
   * @param resolveTimeout Timeout. Up to the discovery-mechanism to adhere to his
   * @return
   */
  def lookup(name: String, resolveTimeout: FiniteDuration): Future[Resolved] =
    lookup(Simple(name), resolveTimeout)

  /**
   * Java API: Perform basic lookup using underlying discovery implementation.
   *
   * While the implementation may provide other settings and ways to configure timeouts,
   * the passed `resolveTimeout` should never be exceeded, as it signals the application's
   * eagerness to wait for a result for this specific lookup.
   *
   * The returned future SHOULD be failed once resolveTimeout has passed.
   */
  def lookup(query: Lookup, resolveTimeout: java.time.Duration): CompletionStage[Resolved] = {
    import scala.compat.java8.FutureConverters._
    lookup(query, FiniteDuration(resolveTimeout.toMillis, TimeUnit.MILLISECONDS)).toJava
  }

  /**
   * Java API
   */
  def lookup(name: String, resolveTimeout: java.time.Duration): CompletionStage[Resolved] =
    lookup(Simple(name), resolveTimeout)

}
