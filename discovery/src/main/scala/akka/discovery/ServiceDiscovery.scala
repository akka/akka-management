/*
 * Copyright (C) 2017 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.discovery

import java.util.concurrent.ConcurrentHashMap
import java.util.function.{ Function => JFunction }

import akka.actor._
import akka.annotation.InternalApi

final class ServiceDiscovery(implicit system: ExtendedActorSystem) extends Extension {

  private val implementations = new ConcurrentHashMap[String, SimpleServiceDiscovery]
  private val factory = new JFunction[String, SimpleServiceDiscovery] {
    override def apply(method: String): SimpleServiceDiscovery = createServiceDiscovery(method)
  }

  private lazy val _defaultImplMethod =
    system.settings.config.getString("akka.discovery.method") match {
      case "<method>" ⇒
        throw new IllegalArgumentException(
            "No default service discovery implementation configured in " +
            "`akka.discovery.method`. Make sure to configure this setting to your preferred implementation such as " +
            "'akka-dns' in your application.conf (from the akka-discovery-dns module).")
      case method ⇒ method
    }

  private lazy val _simpleImpl = loadServiceDiscovery(_defaultImplMethod)

  /**
   * Default [[SimpleServiceDiscovery]] as configured in `akka.discovery.method`.
   */
  @throws[IllegalArgumentException]
  def discovery: SimpleServiceDiscovery = _simpleImpl

  /**
   * Create a [[SimpleServiceDiscovery]] from configuration property.
   * The given `method` parameter is used to find configuration property
   * "akka.discovery.[method].class" or "[method].class". `method` can also
   * be a fully class name.
   *
   * The `SimpleServiceDiscovery` instance for a given `method` will be created
   * once and subsequent requests for the same `method` will return the same instance.
   */
  def loadServiceDiscovery(method: String): SimpleServiceDiscovery = {
    implementations.computeIfAbsent(method, factory)
  }

  private def createServiceDiscovery(method: String): SimpleServiceDiscovery = {
    val config = system.settings.config
    val dynamic = system.dynamicAccess

    def classNameFromConfig(path: String): String =
      if (config.hasPath(path)) config.getString(path)
      else "<nope>"

    def create(clazzName: String) = {
      dynamic
        .createInstanceFor[SimpleServiceDiscovery](clazzName, (classOf[ExtendedActorSystem] → system) :: Nil)
        .recoverWith {
          case _: NoSuchMethodException ⇒
            dynamic.createInstanceFor[SimpleServiceDiscovery](clazzName, (classOf[ActorSystem] → system) :: Nil)
        }
        .recoverWith {
          case _: NoSuchMethodException ⇒
            dynamic.createInstanceFor[SimpleServiceDiscovery](clazzName, Nil)
        }
    }

    val configName = "akka.discovery." + method + ".class"
    val instance = create(classNameFromConfig(configName)).recoverWith {
      case _ ⇒ create(classNameFromConfig(method + ".class"))
    }.recoverWith {
      case _ ⇒ create(method) // so perhaps, it is a classname?
    }

    instance.getOrElse(
      throw new IllegalArgumentException(
          s"Illegal [$configName] value or incompatible class! " +
          "The implementation class MUST extend akka.discovery.SimpleServiceDiscovery and take an " +
          "ExtendedActorSystem as constructor argument.", instance.failed.get)
    )
  }

}

object ServiceDiscovery extends ExtensionId[ServiceDiscovery] with ExtensionIdProvider {
  override def apply(system: ActorSystem): ServiceDiscovery = super.apply(system)

  override def lookup: ServiceDiscovery.type = ServiceDiscovery

  override def get(system: ActorSystem): ServiceDiscovery = super.get(system)

  override def createExtension(system: ExtendedActorSystem): ServiceDiscovery = new ServiceDiscovery()(system)

}
