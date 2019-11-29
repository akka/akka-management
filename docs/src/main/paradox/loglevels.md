# Dynamic Log Levels

Dynamic Log Levels for Logback hooks into Akka Management and provides a route where log levels can be read and set over HTTP.

## Project Info

@@project-info{ projectId="loglevels-logback" }

Requires @ref:[Akka Management](akka-management.md) and that the application uses [Logback](http://logback.qos.ch) as logging backend.

@@dependency[sbt,Gradle,Maven] {
  group=com.lightbend.akka.management
  artifact=loglevel-logback_$scala.binary_version$
  version=$project.version$
  group2=com.lightbend.akka.management
  artifact2=akka-management_$scala.binary.version$
  version2=$project.version$
}

With Akka Management started and this module on the classpath the module is automatically picked up and provides the following two HTTP routes:

### Reading Logger Levels

A HTTP `GET` request to `loglevel?logger=[logger name]` will return the log level of that logger.

### Changing Logger Levels

Only enabled if `akka.management.http.route-providers-read-only` is set to true. 

@@@ warning

If enabling this make sure to properly secure your endpoint with HTTPS and authentication or else anyone with access to the system could change logger levels and potentially do a DoS attack by setting all loggers to `TRACE`.

@@@

A HTTP `POST` request to `loglevel?logger=[logger name]&level=[level name]` will change the logger level of that logger.


