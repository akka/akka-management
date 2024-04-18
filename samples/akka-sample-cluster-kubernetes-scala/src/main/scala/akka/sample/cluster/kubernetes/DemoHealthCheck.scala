package akka.sample.cluster.kubernetes

import scala.concurrent.Future

import akka.actor.ActorSystem
import org.slf4j.LoggerFactory

// Enabled in application.conf
class DemoHealthCheck(system: ActorSystem) extends (() => Future[Boolean]) {
  private val log = LoggerFactory.getLogger(getClass)

  override def apply(): Future[Boolean] = {
    log.info("DemoHealthCheck called")
    Future.successful(true)
  }
}
