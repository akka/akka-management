/*
 * Copyright (C) 2017-2018 Lightbend Inc. <http://www.lightbend.com>
 */

package jdoc.akka.discovery;

import akka.actor.ActorSystem;
import akka.discovery.Lookup;
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
        serviceDiscovery.lookup(Lookup.create("akka.io"), Duration.ofSeconds(1));
        // convenience for a Lookup with only a serviceName
        serviceDiscovery.lookup("akka.io", Duration.ofSeconds(1));
        //#simple

        //#full
        serviceDiscovery.lookup(Lookup.create("akka.io").withPortName("remoting").withProtocol("tcp"), Duration.ofSeconds(1));
        //#full

    }
}
