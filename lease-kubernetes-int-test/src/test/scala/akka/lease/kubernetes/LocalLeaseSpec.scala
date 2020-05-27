/*
 * Copyright (C) 2017-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.coordination.lease.kubernetes
import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory

// For testing locally with a kubectl proxy 8080
// the actual spec is run in kubernetes from Jenkins
abstract class LocalLeaseSpec extends LeaseSpec {
  private lazy val _system = ActorSystem(
    "LocalLeaseSpec",
    ConfigFactory.parseString("""
     akka.loglevel = INFO
    akka.coordination.lease.kubernetes {
      api-service-host = localhost
      api-service-port = 8080
      namespace = "akka-lease-tests"
      namespace-path = ""
      secure-api-server = false
    }
    """)
  )

  override def system = _system
}
