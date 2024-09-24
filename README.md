# Akka (Cluster) Management

The Akka family of projects is managed by teams at [Lightbend](https://lightbend.com/) with help from the community.

This repository contains interfaces to inspect, interact and manage various Parts of Akka, primarily Akka Cluster.
Future additions may extend these concepts to other parts of Akka.

Documentation
-------------

See [reference](https://doc.akka.io/libraries/akka-management/current/) and [API](https://doc.akka.io/api/akka-management/current/akka/management/index.html)

Contributions & Maintainers
---------------------------

*This project does not have contributors, it only has maintainers—frequent and infrequent—and everyone helps out.*
We love new maintainers as well as old maintainers. :-)
The Akka core team keeps an eye on the project to assure its overall coherence but does not fully support these modules.

Contributions are very welcome, see [CONTRIBUTING.md](https://github.com/akka/akka-management/blob/main/CONTRIBUTING.md) or skim [existing tickets](https://github.com/akka/akka-management/issues) to see where you could help out.

Project Status
--------------

With the exception of the community maintained modules listed below version 1.0.0 or later of this library
is ready to be used in production, APIs are stable, and the Lightbend subscription covers support for these modules.

The following modules are maintained by the community and does not have to obey the rule of staying binary compatible
between releases. Breaking API changes may be introduced without notice as we refine and simplify based on feedback.
A module may be dropped in any release without prior deprecation. The Lightbend subscription does not cover support
for the following modules.

* akka-discovery-aws-api
* akka-discovery-aws-api-async
* akka-discovery-marathon-api (End of Life)

License
-------

Akka is licensed under the Business Source License 1.1, please see the [Akka License FAQ](https://www.lightbend.com/akka/license-faq).

Tests and documentation are under a separate license, see the LICENSE file in each documentation and test root directory for details.

