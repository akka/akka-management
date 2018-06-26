/*
 * Copyright (C) 2017 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.discovery

import akka.actor._
import akka.annotation.InternalApi

final class ServiceDiscovery(implicit system: ExtendedActorSystem) extends Extension {

  private lazy val _simpleImplMethod =
    system.settings.config.getString("akka.discovery.method") match {
      case "<method>" ⇒
        throw new IllegalArgumentException(
            "No default service discovery implementation configured in " +
            "`akka.discovery.method`. Make sure to configure this setting to your preferred implementation such as " +
            "'akka-dns' in your application.conf (from the akka-discovery-dns module).")
      case method ⇒ method
    }

  private lazy val _simpleImpl = ServiceDiscovery.loadServiceDiscovery(_simpleImplMethod, system)

  /**
   * Default [[SimpleServiceDiscovery]] as configured in `akka.discovery.method`.
   */
  @throws[IllegalArgumentException]
  def discovery: SimpleServiceDiscovery = _simpleImpl

}

object ServiceDiscovery extends ExtensionId[ServiceDiscovery] with ExtensionIdProvider {
  override def apply(system: ActorSystem): ServiceDiscovery = super.apply(system)

  override def lookup: ServiceDiscovery.type = ServiceDiscovery

  override def get(system: ActorSystem): ServiceDiscovery = super.get(system)

  override def createExtension(system: ExtendedActorSystem): ServiceDiscovery = new ServiceDiscovery()(system)

  /**
   * INTERNAL API
   */
  @InternalApi
  private[akka] def loadServiceDiscovery(method: String, system: ExtendedActorSystem): SimpleServiceDiscovery = {
    val config = system.settings.config
    val dynamic = system.dynamicAccess

    def classNameFromConfig(path: String): String =
      if (config.hasPath(path)) config.getString(path)
      else "<nope>"

    def create(clazzName: String) = {
      dynamic
        .createInstanceFor[SimpleServiceDiscovery](clazzName, (classOf[ExtendedActorSystem] → system) :: Nil)
        .recoverWith {
          case _ ⇒
            dynamic.createInstanceFor[SimpleServiceDiscovery](clazzName, (classOf[ActorSystem] → system) :: Nil)
        }
        .recoverWith {
          case _ ⇒
            dynamic.createInstanceFor[SimpleServiceDiscovery](clazzName, Nil)
        }
    }

    val i = create(classNameFromConfig("akka.discovery." + method + ".class")).recoverWith {
      case _ ⇒ create(classNameFromConfig(method + ".class"))
    }.recoverWith {
      case _ ⇒ create(method) // so perhaps, it is a classname?
    }

    i.getOrElse(
      throw new IllegalArgumentException(
          s"Illegal `akka.discovery.method` value '$method' or incompatible class! " +
          "The implementation class MUST extend akka.discovery.SimpleServiceDiscovery and take an " +
          "ExtendedActorSystem as constructor argument.", i.failed.get)
    )
  }

}
