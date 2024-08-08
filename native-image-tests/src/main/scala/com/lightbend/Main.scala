package com.lightbend

import akka.actor.ExtendedActorSystem
import akka.actor.ExtensionId
import akka.actor.typed.ActorSystem
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.typed.Cluster
import akka.cluster.typed.SelfUp
import akka.cluster.typed.Subscribe
import akka.coordination.lease.LeaseSettings
import akka.management.scaladsl.AkkaManagement

import scala.concurrent.duration.DurationInt

object RootBehavior {

  def checkK8Lease(system: ActorSystem[_]): Unit = {
    // this throws if not all spray-json metadata in place
    new akka.coordination.lease.kubernetes.internal.KubernetesJsonSupport {}

    // we can't really set up the lease but it is expected to be constructed via config/reflection, so let's check access
    // that native-image can't guess
    val exensionNameClass = system.settings.config.getString("akka.coordination.lease.kubernetes.lease-class")
    val clazz = system.dynamicAccess.getClassFor[akka.coordination.lease.scaladsl.Lease](exensionNameClass).get
    // we cant really call it though, but would get a NoSuchMethod here if it can't be found
    clazz.getConstructor(classOf[LeaseSettings], classOf[ExtendedActorSystem])

  }

  def checkK8RollingUpdate(system: ActorSystem[_]): Unit = {
    // this throws if not all spray-json metadata in place
    new akka.rollingupdate.kubernetes.KubernetesJsonSupport {
      override val revisionAnnotation = "deployment.kubernetes.io/revision"
    }
    val extensionClazzName = system.settings.config.getString("pod-cost-class")
    system.dynamicAccess.getObjectFor[ExtensionId[_]](extensionClazzName).get
  }

  def apply(): Behavior[AnyRef] = Behaviors.setup { context =>
    Behaviors.withTimers { timers =>
      // Note that some exceptions in the log from k8 api discovery is expected, see application.conf
      AkkaManagement(context.system).start()
      timers.startSingleTimer("Timeout", 30.seconds)
      Cluster(context.system).subscriptions ! Subscribe(context.self, classOf[SelfUp])

      // best effort coverage of k8 lease and rolling update without actually using them
      checkK8RollingUpdate(context.system)
      checkK8Lease(context.system)

      Behaviors.receiveMessagePartial {
        case SelfUp(_) =>
          context.log.info("Managed to bootstrap cluster, shutting down")
          Behaviors.stopped

        case "Timeout" =>
          context.log.error("Didn't manage to bootstrap within 30s, something is off")
          System.exit(1)
          Behaviors.same
      }
    }
  }
}

object Main extends App {

  val system: ActorSystem[AnyRef] = ActorSystem(RootBehavior(), "ManagementNativeImageTests")

}
