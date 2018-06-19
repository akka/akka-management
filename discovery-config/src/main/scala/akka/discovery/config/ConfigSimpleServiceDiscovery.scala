package akka.discovery.config

import akka.actor.ExtendedActorSystem
import akka.discovery.SimpleServiceDiscovery
import akka.discovery.SimpleServiceDiscovery.{Resolved, ResolvedTarget}
import com.typesafe.config.Config

import scala.collection.JavaConverters._
import scala.collection.{breakOut, immutable}
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

object ConfigServicesParser {
  def parse(config: Config): Map[String, Resolved] = {
    val byService = config.root().entrySet().asScala.map { en =>
      (en.getKey, config.getConfig(en.getKey))
    }.toMap

    byService.map {
      case (serviceName, full) =>
        val x = full.getConfigList("endpoints").asScala
        val cat: immutable.Seq[ResolvedTarget] = x.map { c =>
          val host = c.getString("host")
          val port = if (c.hasPath("port")) Some(c.getInt("port")) else None
          ResolvedTarget(host, port)
        }(breakOut)
        (serviceName, Resolved(serviceName, cat))
    }
  }
}

class ConfigSimpleServiceDiscovery(system: ExtendedActorSystem) extends SimpleServiceDiscovery {

  private val resolvedServices = ConfigServicesParser.parse(
    system.settings.config.getConfig(system.settings.config.getString("akka.discovery.akka-config.services-path"))
  )


  override def lookup(name: String, resolveTimeout: FiniteDuration): Future[SimpleServiceDiscovery.Resolved] = {
    // TODO or fail?
    Future.successful(resolvedServices.getOrElse(name, Resolved(name, immutable.Seq.empty)))
  }
}
