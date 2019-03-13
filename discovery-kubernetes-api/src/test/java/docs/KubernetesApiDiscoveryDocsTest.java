/*
 * Copyright (C) 2017-2018 Lightbend Inc. <https://www.lightbend.com>
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
}
