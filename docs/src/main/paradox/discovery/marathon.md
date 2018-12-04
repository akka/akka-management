## Marathon API

Marathon currently ignores all fields apart from service name. This is expected to change.

If you're a Mesos or DC/OS user, you can use the provided Marathon API implementation. You'll need to add a label
to your Marathon JSON (named `ACTOR_SYSTEM_NAME`  by default) and set the value equal to the name of the configured
effective name, which defaults to your applications actor system name.

You'll also have to add a named port, by default `akkamgmthttp`, and ensure that Akka Management's HTTP interface
is bound to this port.

### Dependencies and usage

This is a separate JAR file:

@@dependency[sbt,Gradle,Maven] {
  group="com.lightbend.akka.discovery"
  artifact="akka-discovery-marathon-api_2.12"
  version="$version$"
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

