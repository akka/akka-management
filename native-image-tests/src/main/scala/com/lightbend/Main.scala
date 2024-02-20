package com.lightbend

import akka.actor.typed.ActorSystem
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.typed.Cluster
import akka.cluster.typed.SelfUp
import akka.cluster.typed.Subscribe
import akka.management.scaladsl.AkkaManagement

import scala.concurrent.duration.DurationInt

object RootBehavior {
  def apply(): Behavior[AnyRef] = Behaviors.setup { context =>
    Behaviors.withTimers { timers =>
      // Note that some exceptions in the log from k8 api discovery is expected, see application.conf
      AkkaManagement(context.system).start()
      timers.startSingleTimer("Timeout", 30.seconds)
      Cluster(context.system).subscriptions ! Subscribe(context.self, classOf[SelfUp])

      // FIXME cover k8 lease
      // FIXME cover rolling-update

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
