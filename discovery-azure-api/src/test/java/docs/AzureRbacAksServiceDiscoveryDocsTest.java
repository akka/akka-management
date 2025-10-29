/*
 * Copyright (C) 2017-2025 Lightbend Inc. <https://akka.io>
 */

package docs;

import akka.actor.ActorSystem;
import akka.discovery.Discovery;
import akka.discovery.ServiceDiscovery;

/*
 AzureRbacAksServiceDiscoveryDocsTest will be used to render docs for azure-rbac-aks-api discovery
 on akka.io.
 */
public class AzureRbacAksServiceDiscoveryDocsTest {
    public void load() {
        ActorSystem system = ActorSystem.create();
        //#azure-rbac-aks-api
        ServiceDiscovery discovery = Discovery.get(system).loadServiceDiscovery("azure-rbac-aks-api");
        //#azure-rbac-aks-api
    }
}
