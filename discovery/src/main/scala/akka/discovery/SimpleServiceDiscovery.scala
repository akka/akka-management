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

    def apply(
        host: String,
        port: Option[Int]
    ): ResolvedTarget = {
      host match {
        case IPv4(_) => new ResolvedTarget(host = host, port = port, address = Some(InetAddress.getByName(host)))
        case _ => new ResolvedTarget(host = host, port = port, address = None)
      }
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
 * Implement to provide basic service discovery mechanism.
 *
 * "Simple" because it's only the basic "lookup" methods, no ability to "keep monitoring a namespace for changes" etc.
 */
@ApiMayChange
abstract class SimpleServiceDiscovery {
  import SimpleServiceDiscovery._

  /**
   * Scala API: Perform basic lookup using underlying discovery implementation.
   *
   * While the implementation may provide other settings and ways to configure timeouts,
   * the passed `resolveTimeout` should never be exceeded, as it signals the application's
   * eagerness to wait for a result for this specific lookup.
   *
   * The returned future SHOULD be failed once resolveTimeout has passed.
   */
  def lookup(name: String, resolveTimeout: FiniteDuration): Future[Resolved]

  /**
   * Java API: Perform basic lookup using underlying discovery implementation.
   *
   * While the implementation may provide other settings and ways to configure timeouts,
   * the passed `resolveTimeout` should never be exceeded, as it signals the application's
   * eagerness to wait for a result for this specific lookup.
   *
   * The returned future SHOULD be failed once resolveTimeout has passed.
   */
  def lookup(name: String, resolveTimeout: java.time.Duration): CompletionStage[Resolved] = {
    import scala.compat.java8.FutureConverters._
    lookup(name, FiniteDuration(resolveTimeout.toMillis, TimeUnit.MILLISECONDS)).toJava
  }

}
