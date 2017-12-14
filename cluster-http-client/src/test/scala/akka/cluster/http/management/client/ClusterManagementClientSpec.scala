package akka.cluster.http.management.client

import akka.actor.ActorSystem
import akka.cluster.http.management.ClusterMember
import akka.http.scaladsl.model._
import akka.stream.ActorMaterializer
import akka.testkit.TestKit
import org.scalamock.scalatest.MockFactory
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FlatSpecLike, Matchers}

import scala.concurrent.Future

class ClusterManagementClientSpec extends TestKit(ActorSystem("akka-management"))
  with Matchers with FlatSpecLike with ScalaFutures {

  val response =
    """
      | {
      | 	"selfNode": "akka.tcp://test@10.10.10.10:1111",
      | 	"members": [{
      | 		"node": "akka.tcp://test@10.10.10.10:1111",
      | 		"nodeUid": "1116964444",
      | 		"status": "Up",
      | 		"roles": ["test","super"]
      | 	},
      |  {
      | 		"node": "akka.tcp://test@10.10.10.10:1112",
      | 		"nodeUid": "1116964443",
      | 		"status": "Up",
      | 		"roles": ["minion"]
      | 	}],
      | 	"unreachable": [],
      | 	"leader": "akka.tcp://test@10.10.10.10:1111",
      | 	"oldest": "akka.tcp://test@10.10.10.10:1111"
      | }
    """.stripMargin


  "The ClusterManagementClient trait" should "look up members by role" in {
    val client = new MockManagementClient(system, Seq(Uri("/members") -> response,
      Uri("/members") -> response, Uri("/members") -> response, Uri("/members") -> response,
      Uri("/members") -> response)) //one for each assertion

    val member1 = ClusterMember("akka.tcp://test@10.10.10.10:1111", "1116964444", "Up", Set("test", "super"))
    val member2 = ClusterMember("akka.tcp://test@10.10.10.10:1112", "1116964443", "Up", Set("minion"))

    whenReady(client.getMembers())(r => r shouldBe Set(member1, member2))
    whenReady(client.getMembers("test"))(r => r shouldBe Set(member1))
    whenReady(client.getMembers("super"))(r => r shouldBe Set(member1))
    whenReady(client.getMembers("minion"))(r => r shouldBe Set(member2))
    whenReady(client.getMembers("test", "minion"))(r => r shouldBe Set(member1, member2))
  }


  "The AkkaClusterManagementClient" should "be properly configured" in {
    val client = new AkkaClusterManagementClient("http://localhost:19999")(system, system.dispatcher)
    client.materializer shouldBe an[ActorMaterializer]
    client.responder shouldBe a[HttpRequest => Future[_]]
  }

}


class MockManagementClient(val system: ActorSystem, reqRespPairs: Seq[(Uri, String)])
  extends ClusterManagementClient with MockFactory {

  val mock = mockFunction[HttpRequest, Future[HttpResponse]]

  override val baseUri = Uri("")

  override implicit val ec = system.dispatcher

  override implicit val materializer = ActorMaterializer()(system)

  override val responder: HttpResponder = mock

  reqRespPairs.foreach {
    case (uri, respString) =>
      val req = HttpRequest(HttpMethods.GET, uri)
      val resp = HttpResponse(status = StatusCodes.OK, entity = HttpEntity(ContentTypes.`application/json`, string = respString))
      mock.expects(req).returning(Future.successful(resp))
  }
}