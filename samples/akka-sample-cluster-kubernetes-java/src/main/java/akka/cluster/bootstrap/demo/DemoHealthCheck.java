package akka.cluster.bootstrap.demo;

import akka.actor.ActorSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

public class DemoHealthCheck implements Supplier<CompletionStage<Boolean>> {
  private final Logger log = LoggerFactory.getLogger(getClass());

  public DemoHealthCheck(ActorSystem system) {
  }

  @Override
  public CompletionStage<Boolean> get() {
    log.info("DemoHealthCheck called");
    return CompletableFuture.completedFuture(true);
  }
}
