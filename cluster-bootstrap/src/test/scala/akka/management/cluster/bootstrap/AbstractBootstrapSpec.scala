/*
 * Copyright (C) 2017-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.management.cluster.bootstrap

import org.scalatest.{ BeforeAndAfterAll, Matchers, WordSpecLike }
import org.scalatest.concurrent.ScalaFutures

abstract class AbstractBootstrapSpec extends WordSpecLike with Matchers with ScalaFutures with BeforeAndAfterAll
