/*
 * Copyright (C) 2017-2018 Lightbend Inc. <http://www.lightbend.com>
 */

package akka.management.http;

import akka.actor.ActorSystem;
import akka.http.javadsl.ConnectionContext;
import akka.http.javadsl.HttpsConnectionContext;
import akka.http.javadsl.server.directives.SecurityDirectives;
import akka.management.AkkaManagement;

import javax.net.ssl.SSLContext;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

/** Compile-only, for documentation code snippets */
public class CodeExamples {
  ActorSystem system = null;

  public void start() {
    SSLContext sslContext = null;
    //#start-akka-management-with-https-context
    AkkaManagement management = AkkaManagement.get(system);

    HttpsConnectionContext https = ConnectionContext.https(sslContext);
    management.setHttpsContext(https);
    management.start();
    //#start-akka-management-with-https-context
  }

  public void basicAuth() {
    AkkaManagement management = null;

    //#basic-auth
    final Function<Optional<SecurityDirectives.ProvidedCredentials>, CompletionStage<Optional<String>>>
      myUserPassAuthenticator = opt -> {
      if (opt.filter(c -> (c != null) && c.verify("p4ssw0rd")).isPresent()) {
        return CompletableFuture.completedFuture(Optional.of(opt.get().identifier()));
      } else {
        return CompletableFuture.completedFuture(Optional.empty());
      }
    };
    // ...
    // FIXME https://github.com/akka/akka-management/issues/338
    // management.setAsyncAuthenticator(myUserPassAuthenticator);
    management.start();
    //#basic-auth
  }

  public void stop() {
    //#stopping
    AkkaManagement httpClusterManagement = AkkaManagement.get(system);
    httpClusterManagement.start();
    //...
    httpClusterManagement.stop();
    //#stopping
  }
}
