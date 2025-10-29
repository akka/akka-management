/*
 * Copyright (C) 2017-2025 Lightbend Inc. <https://akka.io>
 */

package docs;

import akka.actor.ActorSystem;
import akka.discovery.Discovery;
import akka.discovery.ServiceDiscovery;

public class KubernetesApiDiscoveryDocsTest {
  public void loadKubernetesApiDiscovery() {
    ActorSystem system = ActorSystem.create();
    //#kubernetes-api-discovery
    ServiceDiscovery discovery = Discovery.get(system).loadServiceDiscovery("kubernetes-api");
    //#kubernetes-api-discovery
  }

  public void loadExternalKubernetesApiDiscovery() {
    ActorSystem system = ActorSystem.create();
    //#kubernetes-api-for-client-discovery
    ServiceDiscovery discovery = Discovery.get(system).loadServiceDiscovery("kubernetes-api-for-client");
    //#kubernetes-api-for-client-discovery
  }
}
