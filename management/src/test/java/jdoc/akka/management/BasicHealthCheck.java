/*
 * Copyright (C) 2017-2018 Lightbend Inc. <https://www.lightbend.com>
 */

package jdoc.akka.management;

import akka.actor.ActorSystem;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

//#basic
public class BasicHealthCheck implements Supplier<CompletionStage<Boolean>> {

    public BasicHealthCheck(ActorSystem system) {
    }

    @Override
    public CompletionStage<Boolean> get() {
        return CompletableFuture.completedFuture(true);
    }
}
//#basic
