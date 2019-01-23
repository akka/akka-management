/*
 * Copyright (C) 2017-2018 Lightbend Inc. <http://www.lightbend.com>
 */

package akka.management;

import akka.actor.ActorSystem;
import akka.actor.ExtendedActorSystem;
import akka.management.javadsl.HealthChecks;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Test;
import org.scalatest.junit.JUnitSuite;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

import static org.junit.Assert.assertEquals;

public class HealthCheckTest extends JUnitSuite {
    private static Throwable cause = new RuntimeException("oh dear");


    @SuppressWarnings("unused")
    public static class Ok implements Supplier<CompletionStage<Boolean>> {
        @Override
        public CompletionStage<Boolean> get() {
            return CompletableFuture.completedFuture(true);
        }
    }

    @SuppressWarnings("unused")
    public static class NotOk implements Supplier<CompletionStage<Boolean>> {
        public NotOk(ActorSystem system) {
        }

        @Override
        public CompletionStage<Boolean> get() {
            return CompletableFuture.completedFuture(false);
        }
    }

    @SuppressWarnings("unused")
    public static class Throws implements Supplier<CompletionStage<Boolean>> {
        public Throws(ActorSystem system) {
        }

        @Override
        public CompletionStage<Boolean> get() {
            return failed(cause);
        }
    }

    private static ExtendedActorSystem system = (ExtendedActorSystem) ActorSystem.create();

    @Test
    public void okReturnsTrue() throws Exception {
        List<NamedHealthCheck> healthChecks = Collections.singletonList(new NamedHealthCheck("Ok", "akka.management.HealthCheckTest$Ok"));
        HealthChecks checks = new HealthChecks(system, HealthCheckSettings.create(
                healthChecks,
                healthChecks,
                "ready",
                "alive",
                java.time.Duration.ofSeconds(1)
        ));
        assertEquals(checks.alive().toCompletableFuture().get(), true);
        assertEquals(checks.ready().toCompletableFuture().get(), true);
    }

    @Test
    public void notOkayReturnsFalse() throws Exception {
        List<NamedHealthCheck> healthChecks = Collections.singletonList(new NamedHealthCheck("Ok", "akka.management.HealthCheckTest$Ok"));
        HealthChecks checks = new HealthChecks(system, HealthCheckSettings.create(
                healthChecks,
                healthChecks,
                "ready",
                "alive",
                java.time.Duration.ofSeconds(1)
        ));
        assertEquals(checks.alive().toCompletableFuture().get(), true);
        assertEquals(checks.ready().toCompletableFuture().get(), true);
    }

    @Test
    public void throwsReturnsFailed() throws Exception {
        List<NamedHealthCheck> healthChecks = Collections.singletonList(
                new NamedHealthCheck("Throws", "akka.management.HealthCheckTest$Throws"));
        HealthChecks checks = new HealthChecks(system, HealthCheckSettings.create(
                healthChecks,
                healthChecks,
                "ready",
                "alive",
                java.time.Duration.ofSeconds(1)
        ));
        try {
            checks.alive().toCompletableFuture().get();
            Assert.fail("Expected exception");
        } catch (ExecutionException re) {
            assertEquals(re.getCause(), cause);
        }
    }

    @AfterClass
    public static void cleanup() {
        system.terminate();
    }

    private static <R> CompletableFuture<R> failed(Throwable error) {
        CompletableFuture<R> future = new CompletableFuture<>();
        future.completeExceptionally(error);
        return future;
    }
}
