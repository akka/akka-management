/*
 * Copyright (C) 2017 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.discovery

import akka.actor._
import akka.event.Logging
import com.typesafe.config.{ Config, ConfigException, ConfigRenderOptions }

final class ServiceDiscovery(implicit system: ExtendedActorSystem) extends Extension {
  private val log = Logging(system, getClass)

  private lazy val _simpleImplClassConf =
    try system.settings.config.getString("akka.discovery.impl")
    catch {
      case ex: ConfigException.Missing ⇒
        throw new IllegalArgumentException("No default service discovery implementation configured in " +
          "`akka.discovery.impl`. Did you forget to depend on an implementation library such as `akka-discovery-dns`? You may also want to " +
          "configure the implementation to be used explicitly in your application.conf")
    }
  private lazy val _simpleImpl = {
    val i = system.dynamicAccess
      .createInstanceFor[SimpleServiceDiscovery](_simpleImplClassConf, (classOf[ExtendedActorSystem] → system) :: Nil)
      .recoverWith {
        case _ ⇒
          system.dynamicAccess.createInstanceFor[SimpleServiceDiscovery](_simpleImplClassConf,
            (classOf[ActorSystem] → system) :: Nil)
      }
      .recoverWith {
        case _ ⇒ system.dynamicAccess.createInstanceFor[SimpleServiceDiscovery](_simpleImplClassConf, Nil)
      }

    i.getOrElse(
      throw new IllegalArgumentException(
          s"Illegal `akka.discovery.impl` value (${_simpleImplClassConf}) or incompatible class! " +
          "The implementation class MUST extend akka.discovery.SimpleServiceDiscovery and take an ExtendedActorSystem as constructor argument.",
          i.failed.get)
    )
  }

  /**
   * Expose [[SimpleServiceDiscovery]] as configured in `akka.discovery.impl`
   *
   * Could throw an [[IllegalArgumentException]] at first usage if the configured implementation class is illegal.
   */
  // FIXME better name?
  def discovery: SimpleServiceDiscovery = _simpleImpl

}

object ServiceDiscovery extends ExtensionId[ServiceDiscovery] with ExtensionIdProvider {
  override def apply(system: ActorSystem): ServiceDiscovery = super.apply(system)
  override def lookup: ServiceDiscovery.type = ServiceDiscovery

  override def get(system: ActorSystem): ServiceDiscovery = super.get(system)

  override def createExtension(system: ExtendedActorSystem): ServiceDiscovery = new ServiceDiscovery()(system)

}
