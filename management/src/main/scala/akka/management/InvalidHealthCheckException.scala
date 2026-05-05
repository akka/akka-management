/*
 * Copyright (C) 2017-2025 Lightbend Inc. <https://akka.io>
 */

package akka.management

final class InvalidHealthCheckException(msg: String, c: Throwable) extends RuntimeException(msg, c) {
  def this(msg: String) = this(msg, null)
}
