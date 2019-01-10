package akka.management.http

class InvalidHealthCheckException(msg: String, c: Throwable)
      extends RuntimeException(msg, c) {
    def this(msg: String) = this(msg, null)
  }
