/*
 * Copyright (C) 2017 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.cluster.bootstrap.demo;

import akka.actor.AbstractActor;
import akka.cluster.Cluster;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.japi.pf.ReceiveBuilder;

public class ClusterWatcher extends AbstractActor {
  LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);

  Cluster cluster = Cluster.get(context().system());

  @Override
  public Receive createReceive() {
    return ReceiveBuilder.create()
      .matchAny(msg -> {
        log.info("Cluster " + cluster.selfAddress() + " >>> " + msg);
      })
      .build();
  }
}
