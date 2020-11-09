## Marathon API

@@@ warning

This module is community maintained and the Lightbend subscription doesn't cover support for this module.
  It is also marked as @extref:[may change](akka:common/may-change.html).
  That means that the API, configuration or semantics can change without warning or deprecation period.

@@@

Marathon currently ignores all fields apart from service name. This is expected to change.

If you're a Mesos or DC/OS user, you can use the provided Marathon API implementation. You'll need to add a label
to your Marathon JSON (named `ACTOR_SYSTEM_NAME`  by default) and set the value equal to the name of the configured
effective name, which defaults to your applications actor system name.

You'll also have to add a named port, by default `akkamgmthttp`, and ensure that Akka Management's HTTP interface
is bound to this port.

## Project Info

@@project-info{ projectId="akka-discovery-marathon-api" }

### Dependencies and usage

This is a separate JAR file:

@@dependency[sbt,Gradle,Maven] {
  symbol1=AkkaManagementVersion
  value1=$project.version$
  group="com.lightbend.akka.discovery"
  artifact="akka-discovery-marathon-api_$scala.binary.version$"
  version=AkkaManagementVersion
}

`akka-discovery-marathon-api` can be used with Akka $akka.version$ or $akka.version26$ or later.
You have to override the following Akka dependencies by defining them explicitly in your build and
define the Akka version to the one that you are using. Latest patch version of Akka is recommended and
a later version than $akka.version26$ can be used.

@@dependency[sbt,Gradle,Maven] {
  symbol=AkkaVersion
  value=$akka.version26$
  group=com.typesafe.akka
  artifact=akka-cluster_$scala.binary.version$
  version=AkkaVersion
  group2=com.typesafe.akka
  artifact2=akka-discovery_$scala.binary.version$
  version2=AkkaVersion
}

And in your `application.conf`:

```
akka.discovery {
  method = marathon-api
}
```

And in your `marathon.json`:
```
{
   ...
   "cmd": "path-to-your-app -Dakka.remote.netty.tcp.hostname=$HOST -Dakka.remote.netty.tcp.port=$PORT_AKKAREMOTE -Dakka.management.http.hostname=$HOST -Dakka.management.http.port=$PORT_AKKAMGMTHTTP",

   "labels": {
     "ACTOR_SYSTEM_NAME": "my-system"
   },

   "portDefinitions": [
     { "port": 0, "name": "akkaremote" },
     { "port": 0, "name": "akkamgmthttp" }
   ]
   ...
}
```

