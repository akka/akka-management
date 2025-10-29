/*
 * Copyright (C) 2017-2025 Lightbend Inc. <https://akka.io>
 */

package akka.coordination.lease.kubernetes

import akka.actor.CoordinatedShutdown.Reason

object TestFailedReason extends Reason
object TestPassedReason extends Reason
