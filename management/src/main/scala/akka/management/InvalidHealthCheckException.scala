/*
 * Copyright (C) 2017-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.management

final class InvalidHealthCheckException(msg: String, c: Throwable) extends RuntimeException(msg, c) {
  def this(msg: String) = this(msg, null)
}
