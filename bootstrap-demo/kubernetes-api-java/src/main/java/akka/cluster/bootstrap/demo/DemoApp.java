/*
 * Copyright (C) 2017 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.cluster.bootstrap.demo;

import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.cluster.Cluster;
import akka.cluster.ClusterEvent;
import akka.http.javadsl.ConnectHttp;
import akka.http.javadsl.Http;
import akka.http.javadsl.server.AllDirectives;
import akka.management.AkkaManagement;
import akka.management.cluster.bootstrap.ClusterBootstrap;
import akka.stream.ActorMaterializer;
import akka.stream.Materializer;

public class DemoApp extends AllDirectives {

  DemoApp() {
    ActorSystem system = ActorSystem.create("Appka");

    Materializer mat = ActorMaterializer.create(system);
    Cluster cluster = Cluster.get(system);

    system.log().info("Started [" + system + "], cluster.selfAddress = " + cluster.selfAddress() + ")");

    //#start-akka-management
    AkkaManagement.get(system).start();
    //#start-akka-management
    ClusterBootstrap.get(system).start();

    cluster
      .subscribe(system.actorOf(Props.create(ClusterWatcher.class)), ClusterEvent.initialStateAsEvents(), ClusterEvent.ClusterDomainEvent.class);

    Http.get(system).bindAndHandle(complete("Hello world").flow(system, mat), ConnectHttp.toHost("0.0.0.0", 8080), mat);

    cluster.registerOnMemberUp(() -> {
      system.log().info("Cluster member is up!");
    });
  }

  public static void main(String[] args) {
    new DemoApp();
  }
}

