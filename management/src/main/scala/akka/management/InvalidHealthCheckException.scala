/*
 * Copyright (C) 2017-2018 Lightbend Inc. <http://www.lightbend.com>
 */

package akka.management.http

final class InvalidHealthCheckException(msg: String, c: Throwable) extends RuntimeException(msg, c) {
  def this(msg: String) = this(msg, null)
}
