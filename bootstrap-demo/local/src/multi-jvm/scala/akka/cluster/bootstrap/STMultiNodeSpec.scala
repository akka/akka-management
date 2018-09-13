package akka.cluster.bootstrap

import akka.remote.testkit.{MultiNodeSpec, MultiNodeSpecCallbacks}

import scala.language.implicitConversions
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}
import org.scalatest.Matchers

trait STMultiNodeSpec extends MultiNodeSpecCallbacks
  with WordSpecLike with Matchers with BeforeAndAfterAll { self: MultiNodeSpec ⇒

  override def beforeAll() = multiNodeSpecBeforeAll()

  override def afterAll() = multiNodeSpecAfterAll()

  override implicit def convertToWordSpecStringWrapper(s: String): WordSpecStringWrapper = new WordSpecStringWrapper(s"$s (on node '${self.myself.name}', $getClass)")
}
