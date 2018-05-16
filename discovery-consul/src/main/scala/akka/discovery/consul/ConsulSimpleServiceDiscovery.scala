/*
 * Copyright (C) 2017 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.discovery.consul

import java.util
import java.util.concurrent.TimeoutException

import akka.actor.ActorSystem
import akka.discovery.SimpleServiceDiscovery

import scala.collection.immutable.Seq
import scala.concurrent.{ ExecutionContext, Future, Promise }
import akka.pattern.after
import com.google.common.net.HostAndPort
import com.orbitz.consul.Consul
import com.orbitz.consul.async.ConsulResponseCallback
import com.orbitz.consul.model.ConsulResponse
import ConsulSimpleServiceDiscovery._
import akka.discovery.SimpleServiceDiscovery.{ Resolved, ResolvedTarget }
import com.orbitz.consul.model.catalog.CatalogService
import com.orbitz.consul.option.QueryOptions

import scala.collection.JavaConverters._
import scala.concurrent.duration.FiniteDuration
import scala.util.Try

class ConsulSimpleServiceDiscovery(system: ActorSystem) extends SimpleServiceDiscovery {

  private lazy val settings = ConsulSettings.get(system)
  private lazy val consul =
    Consul.builder().withHostAndPort(HostAndPort.fromParts(settings.consulHost, settings.consulPort)).build()

  override def lookup(name: String, resolveTimeout: FiniteDuration): Future[SimpleServiceDiscovery.Resolved] = {
    import system.dispatcher
    Future.firstCompletedOf(
      Seq(
        after(resolveTimeout, using = system.scheduler)(
          Future.failed(new TimeoutException("Future timed out!"))
        ),
        lookupInConsul(name)
      )
    )
  }

  private def lookupInConsul(name: String)(implicit executionContext: ExecutionContext): Future[Resolved] = {
    val consulResult = for {
      servicesWithTags <- getServicesWithTags
      serviceIds = servicesWithTags.getResponse
        .entrySet()
        .asScala
        .filter(e => e.getValue.contains(settings.applicationNameTagPrefix + name))
        .map(_.getKey)
      catalogServices <- Future.sequence(serviceIds.map(id => getService(id).map(_.getResponse.asScala.toList)))
      resolvedTargets = catalogServices.flatten.toSeq.map(
          catalogService => extractResolvedTargetFromCatalogService(catalogService))
    } yield resolvedTargets
    consulResult.map(targets => Resolved(name, scala.collection.immutable.Seq(targets: _*)))
  }

  private def extractResolvedTargetFromCatalogService(catalogService: CatalogService) = {
    val port = catalogService.getServiceTags.asScala
      .find(_.startsWith(settings.applicationAkkaManagementPortTagPrefix))
      .map(_.replace(settings.applicationAkkaManagementPortTagPrefix, ""))
      .flatMap { maybePort =>
        Try(maybePort.toInt).toOption
      }
    ResolvedTarget(catalogService.getServiceAddress, Some(port.getOrElse(catalogService.getServicePort)))
  }

  private val getServicesWithTags = ((callback: ConsulResponseCallback[util.Map[String, util.List[String]]]) =>
                                       consul.catalogClient().getServices(callback)).asFuture

  private def getService(name: String) =
    ((callback: ConsulResponseCallback[util.List[CatalogService]]) =>
       consul.catalogClient().getService(name, QueryOptions.BLANK, callback)).asFuture

}

object ConsulSimpleServiceDiscovery {

  implicit class ConsulResponseFutureDecorator[T](f: ConsulResponseCallback[T] => Unit) {
    def asFuture: Future[ConsulResponse[T]] = {
      val callback = new ConsulResponseFutureCallback[T]
      f(callback)
      callback.future
    }
  }

  case class ConsulResponseFutureCallback[T]() extends ConsulResponseCallback[T] {

    private val promise = Promise[ConsulResponse[T]]

    def future: Future[ConsulResponse[T]] = promise.future

    override def onComplete(consulResponse: ConsulResponse[T]): Unit = {
      promise.success(consulResponse)
    }

    override def onFailure(throwable: Throwable): Unit = {
      promise.failure(throwable)
    }
  }

}
