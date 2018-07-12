/*
 * Copyright (C) 2017 Lightbend Inc. <http://www.lightbend.com>
 */
package jdoc.akka.discovery;

import akka.actor.ActorSystem;
import akka.discovery.ServiceDiscovery;
import akka.discovery.SimpleServiceDiscovery;

import java.time.Duration;

public class CompileOnlyTest {
    public static void example() {
        //#loading
        ActorSystem as = ActorSystem.create();
        SimpleServiceDiscovery serviceDiscovery = ServiceDiscovery.get(as).discovery();
        //#loading

        //#simple
        serviceDiscovery.lookup("akka.io", Duration.ofSeconds(1));
        serviceDiscovery.lookup(new SimpleServiceDiscovery.Simple("akka.io"), Duration.ofSeconds(1));
        //#simple

        //#full
        serviceDiscovery.lookup(new SimpleServiceDiscovery.Full("akka.io", "remoting", "tcp"), Duration.ofSeconds(1));
        //#full

    }
}
