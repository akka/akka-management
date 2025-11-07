Akka
====
*Akka is a powerful platform that simplifies building and operating highly responsive, resilient, and scalable services.*


The platform consists of
* the [**Akka SDK**](https://doc.akka.io/) for straightforward, rapid development with AI assist and automatic clustering. Services built with the Akka SDK are automatically clustered and can be deployed on any infrastructure.
* and [**Akka Automated Operations**](https://doc.akka.io/operations/akka-platform.html), a managed solution that handles everything for Akka SDK services from auto-elasticity to multi-region high availability running safely within your VPC.

The **Akka SDK** and **Akka Automated Operations** are built upon the foundational [**Akka libraries**](https://doc.akka.io/libraries/akka-dependencies/current/), providing the building blocks for distributed systems.


Akka Management
===============

This library contains interfaces to inspect, interact and manage various Parts of Akka core, primarily Akka Cluster.
Future additions may extend these concepts to other parts of Akka libraries.

Reference Documentation
-----------------------

The reference documentation for all Akka libraries is available via [doc.akka.io/libraries/](https://doc.akka.io/libraries/), details for the Akka Management library
for [Scala](https://doc.akka.io/libraries/akka-management/current/?language=scala) and [Java](https://doc.akka.io/libraries/akka-management/current/?language=java).

The current versions of all Akka libraries are listed on the [Akka Dependencies](https://doc.akka.io/libraries/akka-dependencies/current/) page. Releases of the Akka Management library in this repository are listed on the [GitHub releases](https://github.com/akka/akka-management/releases) page.


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

Akka is licensed under the Business Source License 1.1, please see the [Akka License FAQ](https://akka.io/bsl-license-faq).

Tests and documentation are under a separate license, see the LICENSE file in each documentation and test root directory for details.

