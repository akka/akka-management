/*
 * Copyright (C) 2017-2025 Lightbend Inc. <https://akka.io>
 */

package akka.management.cluster.bootstrap

import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

abstract class AbstractBootstrapSpec extends AnyWordSpecLike with Matchers with ScalaFutures with BeforeAndAfterAll
