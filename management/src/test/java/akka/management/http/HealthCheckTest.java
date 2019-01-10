package akka.management.http;

import akka.actor.ActorSystem;
import akka.actor.ExtendedActorSystem;
import akka.management.http.javadsl.HealthChecks;
import org.junit.AfterClass;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

import static org.junit.Assert.assertEquals;

public class HealthCheckTest {
    public static class Ok implements Supplier<CompletionStage<Boolean>> {
        public Ok(ExtendedActorSystem eas) { }

        @Override
        public CompletionStage<Boolean> get() {
            return CompletableFuture.completedFuture(true);
        }
    }

    private static ExtendedActorSystem eas = (ExtendedActorSystem) ActorSystem.create();

    @Test
    public void beUseableFromJava() throws Exception {
        List<String> healthChecks = Arrays.asList("akka.management.http.HealthCheckTest$Ok");
        HealthChecks checks = new HealthChecks(eas, HealthCheckSettings.create(
                healthChecks,
                healthChecks,
                "ready",
                "alive"
        ));
        assertEquals(checks.alive().toCompletableFuture().get(), true);
    }

    @AfterClass
    public static void cleanup() {
        eas.terminate();
    }

}
