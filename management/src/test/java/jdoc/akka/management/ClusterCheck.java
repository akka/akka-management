/*
 * Copyright (C) 2017-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package jdoc.akka.management;

import akka.actor.ActorSystem;
import akka.cluster.Cluster;
import akka.cluster.MemberStatus;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

//#cluster
class ClusterCheck implements Supplier<CompletionStage<Boolean>> {

    private final Cluster cluster;

    public ClusterCheck(ActorSystem system) {
        cluster = Cluster.get(system);
    }

    @Override
    public CompletionStage<Boolean> get() {
        return CompletableFuture.completedFuture(cluster.selfMember().status() == MemberStatus.up());
    }
}
//#cluster
