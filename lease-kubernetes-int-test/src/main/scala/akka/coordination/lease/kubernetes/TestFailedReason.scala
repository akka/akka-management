/*
 * Copyright (C) 2017-2023 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.coordination.lease.kubernetes

import akka.actor.CoordinatedShutdown.Reason

object TestFailedReason extends Reason
object TestPassedReason extends Reason
