package akka.cluster.http.management.client

import akka.actor.ActorSystem
import akka.cluster.http.management.{ClusterHttpManagementJsonProtocol, ClusterMember, ClusterMembers}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.Uri.Path
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, Uri}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.{ActorMaterializer, Materializer}

import scala.concurrent.{ExecutionContext, Future}

trait ClusterManagementClient extends ClusterHttpManagementJsonProtocol {

  type HttpResponder = HttpRequest => Future[HttpResponse]

  def responder: HttpResponder

  def baseUri: Uri

  private[akka] implicit def system: ActorSystem

  private[akka] implicit def materializer: Materializer

  private[akka] implicit def ec: ExecutionContext

  def getMembers(roles: String*): Future[Set[ClusterMember]] = {
    val roleFilter: ClusterMember => Boolean = (m) => roles.isEmpty || roles.exists(m.roles.contains(_))
    responder(HttpRequest(uri = baseUri.withPath(Path("/members"))))
      .flatMap(r => Unmarshal(r.entity).to[ClusterMembers])
      .map(c => c.members.filter(roleFilter))
  }
}

class AkkaClusterManagementClient(val baseUri: Uri)(implicit val system: ActorSystem, val ec: ExecutionContext)
  extends ClusterManagementClient {
  override private[akka] implicit val materializer = ActorMaterializer()

  override def responder = Http().singleRequest(_)
}
