Your Akka Cluster on DC/OS
==========================

Prerequisites
-------------

* A DC/OS cluster with `dcos` command configured to point at it
* SBT
* Webserver to host build artifact
* A terminal with working directory `bootstrap-demo/marathon-api`

Step 1: Package the App
-----------------------

Compile and package the demo app.

```bash
sbt universal:packageZipTarball
```

Now, in the `target/universal` folder, you should have a
`bootstrap-demo-marathon-api-0.1.0.tgz` file.

Step 2: Upload the App
----------------------

Upload `bootstrap-demo-marathon-api-0.1.0.tgz` to a webserver that your DC/OS cluster can access.


Step 3: Edit Config
-------------------

Edit `marathon/app.json`, replacing the value at `fetch[0].uri` ("http://yourwebserver.example.com/bootstrap-demo-marathon-api-0.1.0.tgz")
with the URL of the packaged application.

Step 4: Deploy the App
----------------------

Using the `dcos` command, deploy the application:

```bash
dcos marathon app add dcos/marathon.json
```

Step 5: Verify the cluster formation
------------------------------------

Using `dcos task log` you can watch the output of the application.

```bash
dcos task log --follow bootstrap-demo-marathon-api-0.1.0
```

Successful log output will look something like the following:

```
[INFO] [01/11/2018 21:19:48.113] [my-system-akka.actor.default-dispatcher-6] [akka.cluster.Cluster(akka://my-system)] Cluster Node [akka.tcp://my-system@192.168.65.111:14878] - Sending InitJoinAck message from node [akka.tcp://my-system@192.168.65.111:14878] to [Actor[akka.tcp://my-system@192.168.65.111:7664/system/cluster/core/daemon/joinSeedNodeProcess-1#-1012708477]]
[INFO] [01/11/2018 21:19:48.145] [my-system-akka.actor.default-dispatcher-6] [akka.cluster.Cluster(akka://my-system)] Cluster Node [akka.tcp://my-system@192.168.65.111:14878] - Node [akka.tcp://my-system@192.168.65.111:7664] is JOINING, roles [dc-default]
[INFO] [01/11/2018 21:19:49.139] [my-system-akka.actor.default-dispatcher-18] [akka.cluster.Cluster(akka://my-system)] Cluster Node [akka.tcp://my-system@192.168.65.111:14878] - Leader is moving node [akka.tcp://my-system@192.168.65.111:7664] to [Up]
```
