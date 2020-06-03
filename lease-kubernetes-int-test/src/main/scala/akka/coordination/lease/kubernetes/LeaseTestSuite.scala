/*
 * Copyright (C) 2017-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.coordination.lease.kubernetes

import akka.actor.{ ActorSystem, CoordinatedShutdown }

import scala.util.{ Failure, Success, Try }

object LeaseTestSuite {

  def main(args: Array[String]): Unit = {
    val as = ActorSystem("LeaseTestSuite")
    val log = as.log
    log.info("Running test")

    val leaseSpec = new LeaseSpec {
      override def system: ActorSystem = as
    }
    val testSuite = Try(leaseSpec.execute(stats = true))

    log.info("Test complete {}", testSuite)

    testSuite match {
      case Success(value) =>
        log.info("Test succeeded", value)
        CoordinatedShutdown(as).run(TestPassedReason)
      case Failure(exception) =>
        log.error(exception, "Test failed")
        CoordinatedShutdown(as).run(TestFailedReason)
    }
  }

}
