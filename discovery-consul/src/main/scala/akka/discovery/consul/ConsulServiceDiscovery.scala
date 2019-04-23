/*
 * Copyright (C) 2017-2018 Lightbend Inc. <http://www.lightbend.com>
 */

package akka.discovery.consul

import java.net.InetAddress
import java.util
import java.util.concurrent.TimeoutException

import akka.actor.ActorSystem
import akka.annotation.ApiMayChange
import akka.discovery.ServiceDiscovery.{ Resolved, ResolvedTarget }
import akka.discovery.{ Lookup, ServiceDiscovery }
import akka.pattern.after
import com.google.common.net.HostAndPort
import com.orbitz.consul.Consul
import com.orbitz.consul.async.ConsulResponseCallback
import com.orbitz.consul.model.ConsulResponse
import com.orbitz.consul.model.catalog.CatalogService
import com.orbitz.consul.option.QueryOptions

import scala.collection.JavaConverters._
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ ExecutionContext, Future, Promise }
import scala.util.Try

@ApiMayChange
class ConsulServiceDiscovery(system: ActorSystem) extends ServiceDiscovery {

  private val settings = ConsulSettings.get(system)
  private val consul =
    Consul.builder().withHostAndPort(HostAndPort.fromParts(settings.consulHost, settings.consulPort)).build()

  override def lookup(lookup: Lookup, resolveTimeout: FiniteDuration): Future[Resolved] = {
    implicit val ec: ExecutionContext = system.dispatcher
    Future.firstCompletedOf(
      Seq(
        after(resolveTimeout, using = system.scheduler)(
          Future.failed(new TimeoutException(s"Lookup for [$lookup] timed-out, within [$resolveTimeout]!"))
        ),
        lookupInConsul(lookup.serviceName)
      )
    )
  }

  private def lookupInConsul(name: String)(implicit executionContext: ExecutionContext): Future[Resolved] = {
    getService(name + settings.managementServiceSuffix).map {
      _.getResponse.asScala.map { catalogService =>
        ResolvedTarget(
          host = catalogService.getServiceAddress match {
            case "" | null => catalogService.getAddress
            case serviceAddress => serviceAddress
          },
          port = Some(catalogService.getServicePort),
          address = Try(InetAddress.getByName(catalogService.getAddress)).toOption
        )
      }
    } map { targets =>
      Resolved(name, targets.toList)
    }
  }

  private def getService(name: String): Future[ConsulResponse[util.List[CatalogService]]] = {
    system.log.info(s"looking for akka management service by $name in consul")
    val promise = Promise[ConsulResponse[util.List[CatalogService]]]
    consul
      .catalogClient()
      .getService(
        name,
        QueryOptions.BLANK,
        new ConsulResponseCallback[util.List[CatalogService]] {
          override def onComplete(consulResponse: ConsulResponse[util.List[CatalogService]]): Unit =
            promise.success(consulResponse)
          override def onFailure(throwable: Throwable): Unit =
            promise.failure(throwable)
        }
      )
    promise.future
  }
}
