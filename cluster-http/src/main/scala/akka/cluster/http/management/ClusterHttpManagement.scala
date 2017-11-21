/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.cluster.http.management

import java.util.concurrent.atomic.AtomicReference

import akka.Done
import akka.actor.AddressFromURIString
import akka.cluster.sharding.{ ClusterSharding, ShardRegion }
import akka.cluster.{ Cluster, Member, MemberStatus }
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{ Route, RouteResult }
import akka.http.scaladsl.{ ConnectionContext, Http }
import akka.pattern.{ ask, AskTimeoutException }
import akka.stream.ActorMaterializer
import akka.util.Timeout
import spray.json.DefaultJsonProtocol

import scala.concurrent.duration._
import scala.concurrent.{ Future, Promise }

final case class ClusterUnreachableMember(node: String, observedBy: Seq[String])
final case class ClusterMember(node: String, nodeUid: String, status: String, roles: Set[String])
final case class ClusterMembers(selfNode: String,
                                members: Set[ClusterMember],
                                unreachable: Seq[ClusterUnreachableMember],
                                leader: Option[String],
                                oldest: Option[String])
final case class ClusterHttpManagementMessage(message: String)
final case class ShardRegionInfo(shardId: String, numEntities: Int)
final case class ShardDetails(regions: Seq[ShardRegionInfo])

private[akka] sealed trait ClusterHttpManagementOperation
private[akka] case object Down extends ClusterHttpManagementOperation
private[akka] case object Leave extends ClusterHttpManagementOperation
private[akka] case object Join extends ClusterHttpManagementOperation

object ClusterHttpManagementOperation {
  def fromString(value: String): Option[ClusterHttpManagementOperation] =
    Vector(Down, Leave, Join).find(_.toString.equalsIgnoreCase(value))
}

trait ClusterHttpManagementJsonProtocol extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val clusterUnreachableMemberFormat = jsonFormat2(ClusterUnreachableMember)
  implicit val clusterMemberFormat = jsonFormat4(ClusterMember)
  implicit val clusterMembersFormat = jsonFormat5(ClusterMembers)
  implicit val clusterMemberMessageFormat = jsonFormat1(ClusterHttpManagementMessage)
  implicit val shardRegionInfoFormat = jsonFormat2(ShardRegionInfo)
  implicit val shardDetailsFormat = jsonFormat1(ShardDetails)
}

trait ClusterHttpManagementHelper extends ClusterHttpManagementJsonProtocol {
  def memberToClusterMember(m: Member): ClusterMember =
    ClusterMember(s"${m.uniqueAddress.address}", s"${m.uniqueAddress.longUid}", s"${m.status}", m.roles)
}

object ClusterHttpManagementRoutes extends ClusterHttpManagementHelper {

  private def routeGetMembers(cluster: Cluster) =
    get {
      complete {
        val members = cluster.readView.state.members.map(memberToClusterMember)

        val unreachable = cluster.readView.reachability.observersGroupedByUnreachable.toSeq.sortBy(_._1).map {
          case (subject, observers) ⇒
            ClusterUnreachableMember(s"${subject.address}", observers.toSeq.sorted.map(m ⇒ s"${m.address}"))
        }

        val leader = cluster.readView.leader.map(_.toString)
        val oldest = cluster.state.members.toSeq
          .filter(node => node.status == MemberStatus.Up && node.dataCenter == cluster.selfDataCenter)
          .sorted(Member.ageOrdering)
          .headOption // we are only interested in the oldest one that is still Up
          .map(_.address.toString)

        ClusterMembers(s"${cluster.readView.selfAddress}", members, unreachable, leader, oldest)
      }
    }

  private def routePostMembers(cluster: Cluster) =
    post {
      formField('address) { addressString ⇒
        complete {
          val address = AddressFromURIString(addressString)
          cluster.join(address)
          ClusterHttpManagementMessage(s"Joining $address")
        }
      }
    }

  private def routeGetMember(cluster: Cluster, member: Member) =
    get {
      complete {
        memberToClusterMember(member)
      }
    }

  private def routeDeleteMember(cluster: Cluster, member: Member) =
    delete {
      complete {
        cluster.leave(member.uniqueAddress.address)
        ClusterHttpManagementMessage(s"Leaving ${member.uniqueAddress.address}")
      }
    }

  private def routePutMember(cluster: Cluster, member: Member) =
    put {
      formField('operation) { operation ⇒
        ClusterHttpManagementOperation.fromString(operation) match {
          case Some(Down) ⇒
            cluster.down(member.uniqueAddress.address)
            complete(ClusterHttpManagementMessage(s"Downing ${member.uniqueAddress.address}"))
          case Some(Leave) ⇒
            cluster.leave(member.uniqueAddress.address)
            complete(ClusterHttpManagementMessage(s"Leaving ${member.uniqueAddress.address}"))
          case _ ⇒
            complete(StatusCodes.BadRequest → ClusterHttpManagementMessage("Operation not supported"))
        }
      }
    }

  private def findMember(cluster: Cluster, memberAddress: String): Option[Member] =
    cluster.readView.members.find(
        m ⇒ s"${m.uniqueAddress.address}" == memberAddress || m.uniqueAddress.address.hostPort == memberAddress)

  private def routesMember(cluster: Cluster) =
    path(Remaining) { memberAddress ⇒
      findMember(cluster, memberAddress) match {
        case Some(member) ⇒
          routeGetMember(cluster, member) ~ routeDeleteMember(cluster, member) ~ routePutMember(cluster, member)
        case None ⇒
          complete(StatusCodes.NotFound → ClusterHttpManagementMessage(s"Member [$memberAddress] not found"))
      }
    }

  private def routeGetShardInfo(cluster: Cluster, shardRegionName: String) =
    get {
      extractExecutionContext { implicit executor =>
        complete {
          implicit val timeout = Timeout(5.seconds)
          try {
            ClusterSharding(cluster.system)
              .shardRegion(shardRegionName)
              .ask(ShardRegion.GetShardRegionStats)
              .mapTo[ShardRegion.ShardRegionStats]
              .map { shardRegionStats =>
                ShardDetails(shardRegionStats.stats.map(s => ShardRegionInfo(s._1, s._2)).toSeq)
              }
          } catch {
            case _: AskTimeoutException =>
              StatusCodes.NotFound → ClusterHttpManagementMessage(
                  s"Shard Region $shardRegionName not responding, may have been terminated")
            case _: IllegalArgumentException =>
              StatusCodes.NotFound → ClusterHttpManagementMessage(s"Shard Region $shardRegionName is not started")
          }
        }
      }
    }

  /**
   * Creates an instance of [[akka.cluster.http.management.ClusterHttpManagementRoutes]] to manage the specified
   * [[akka.cluster.Cluster]] instance. This version does not provide Basic Authentication. It uses
   * the default path "members".
   */
  def apply(cluster: Cluster): Route = apply(cluster, "")

  /**
   * Creates an instance of [[akka.cluster.http.management.ClusterHttpManagementRoutes]] to manage the specified
   * [[akka.cluster.Cluster]] instance. This version does not provide Basic Authentication. It uses
   * the specified path `pathPrefixName`.
   */
  def apply(cluster: Cluster, pathPrefixName: String): Route = {
      val basePath = if (pathPrefixName.isEmpty) rawPathPrefix(pathPrefixName) else pathPrefix(pathPrefixName)

    basePath {
      pathPrefix("members") {
        pathEndOrSingleSlash {
          routeGetMembers(cluster) ~ routePostMembers(cluster)
        } ~
        routesMember(cluster)
      } ~
      pathPrefix("shards" / Remaining) { shardRegionName =>
        pathEnd {
          routeGetShardInfo(cluster, shardRegionName)
        }
      }
    }
  }

  /**
   * Creates an instance of [[akka.cluster.http.management.ClusterHttpManagementRoutes]] to manage the specified
   * [[akka.cluster.Cluster]] instance. This version provides Basic Authentication through the specified
   * AsyncAuthenticator. It uses the default path "members".
   */
  def apply(cluster: Cluster, asyncAuthenticator: AsyncAuthenticator[String]): Route =
    authenticateBasicAsync[String](realm = "secured", asyncAuthenticator) { _ ⇒
      apply(cluster)
    }

  /**
   * Creates an instance of [[akka.cluster.http.management.ClusterHttpManagementRoutes]] to manage the specified
   * [[akka.cluster.Cluster]] instance. This version provides Basic Authentication through the specified
   * AsyncAuthenticator. It uses the specified path `pathPrefixName`.
   */
  def apply(cluster: Cluster, pathPrefixName: String, asyncAuthenticator: AsyncAuthenticator[String]): Route =
    authenticateBasicAsync[String](realm = "secured", asyncAuthenticator) { _ ⇒
      apply(cluster, pathPrefixName)
    }
}

object ClusterHttpManagement {

  /**
   * Creates an instance of [[akka.cluster.http.management.ClusterHttpManagement]] to manage the specified
   * [[akka.cluster.Cluster]] instance. This version does not provide security (Basic Authentication or SSL)
   * and uses the default path "members".
   */
  def apply(cluster: Cluster): ClusterHttpManagement =
    new ClusterHttpManagement(cluster)

  /**
   * Creates an instance of [[akka.cluster.http.management.ClusterHttpManagement]] to manage the specified
   * [[akka.cluster.Cluster]] instance. This version does not provide security (Basic Authentication or SSL).
   * It uses the specified path `pathPrefix`.
   */
  def apply(cluster: Cluster, pathPrefix: String): ClusterHttpManagement =
    new ClusterHttpManagement(cluster, Some(pathPrefix), None, None)

  /**
   * Creates an instance of [[akka.cluster.http.management.ClusterHttpManagement]] to manage the specified
   * [[akka.cluster.Cluster]] instance. This version provides Basic Authentication with the specified
   * AsyncAuthenticator. It does not provide SSL security. It uses the default path "members".
   */
  def apply(cluster: Cluster, asyncAuthenticator: AsyncAuthenticator[String]): ClusterHttpManagement =
    new ClusterHttpManagement(cluster, None, Some(asyncAuthenticator), None)

  /**
   * Creates an instance of [[akka.cluster.http.management.ClusterHttpManagement]] to manage the specified
   * [[akka.cluster.Cluster]] instance. This version provides SSL with the specified ConnectionContext.
   * It does not provide Basic Authentication. It uses the default path "members".
   */
  def apply(cluster: Cluster, https: ConnectionContext): ClusterHttpManagement =
    new ClusterHttpManagement(cluster, None, None, Some(https))

  /**
   * Creates an instance of [[akka.cluster.http.management.ClusterHttpManagement]] to manage the specified
   * [[akka.cluster.Cluster]] instance. This version provides Basic Authentication with the specified
   * AsyncAuthenticator. It does not provide SSL security. It uses the specified path `pathPrefix`.
   */
  def apply(cluster: Cluster,
            pathPrefix: String,
            asyncAuthenticator: AsyncAuthenticator[String]): ClusterHttpManagement =
    new ClusterHttpManagement(cluster, Some(pathPrefix), Some(asyncAuthenticator), None)

  /**
   * Creates an instance of [[akka.cluster.http.management.ClusterHttpManagement]] to manage the specified
   * [[akka.cluster.Cluster]] instance. This version provides SSL with the specified ConnectionContext.
   * It does not provide Basic Authentication. It uses the specified path `pathPrefix`.
   */
  def apply(cluster: Cluster, pathPrefix: String, https: ConnectionContext) =
    new ClusterHttpManagement(cluster, Some(pathPrefix), None, Some(https))

  /**
   * Creates an instance of [[akka.cluster.http.management.ClusterHttpManagement]] to manage the specified
   * [[akka.cluster.Cluster]] instance. This version provides Basic Authentication with the specified
   * AsyncAuthenticator. It provide SSL with the specified ConnectionContext. It uses the default path "members".
   */
  def apply(cluster: Cluster,
            asyncAuthenticator: AsyncAuthenticator[String],
            https: ConnectionContext): ClusterHttpManagement =
    new ClusterHttpManagement(cluster, None, Some(asyncAuthenticator), Some(https))

  /**
   * Creates an instance of [[akka.cluster.http.management.ClusterHttpManagement]] to manage the specified
   * [[akka.cluster.Cluster]] instance. This version provides Basic Authentication with the specified
   * AsyncAuthenticator. It provide SSL with the specified ConnectionContext. It uses the specified path `pathPrefix`.
   */
  def apply(cluster: Cluster,
            pathPrefix: String,
            asyncAuthenticator: AsyncAuthenticator[String],
            https: ConnectionContext): ClusterHttpManagement =
    new ClusterHttpManagement(cluster, Some(pathPrefix), Some(asyncAuthenticator), Some(https))

  /**
   * Creates an instance of [[akka.cluster.http.management.ClusterHttpManagement]] to manage the specified
   * [[akka.cluster.Cluster]] instance. This version does not provide security (Basic Authentication or SSL)
   * and uses the default path "members".
   */
  def create(cluster: Cluster): ClusterHttpManagement =
    apply(cluster)

  /**
   * Creates an instance of [[akka.cluster.http.management.ClusterHttpManagement]] to manage the specified
   * [[akka.cluster.Cluster]] instance. This version does not provide security (Basic Authentication or SSL).
   * It uses the specified path `pathPrefix`.
   */
  def create(cluster: Cluster, pathPrefix: String): ClusterHttpManagement =
    apply(cluster, pathPrefix)

  /**
   * Creates an instance of [[akka.cluster.http.management.ClusterHttpManagement]] to manage the specified
   * [[akka.cluster.Cluster]] instance. This version provides Basic Authentication with the specified
   * AsyncAuthenticator. It does not provide SSL security. It uses the default path "members".
   */
  def create(cluster: Cluster, asyncAuthenticator: AsyncAuthenticator[String]): ClusterHttpManagement =
    apply(cluster, asyncAuthenticator)

  /**
   * Creates an instance of [[akka.cluster.http.management.ClusterHttpManagement]] to manage the specified
   * [[akka.cluster.Cluster]] instance. This version provides SSL with the specified ConnectionContext.
   * It does not provide Basic Authentication. It uses the default path "members".
   */
  def create(cluster: Cluster, https: ConnectionContext): ClusterHttpManagement =
    apply(cluster, https)

  /**
   * Creates an instance of [[akka.cluster.http.management.ClusterHttpManagement]] to manage the specified
   * [[akka.cluster.Cluster]] instance. This version provides Basic Authentication with the specified
   * AsyncAuthenticator. It does not provide SSL security. It uses the specified path `pathPrefix`.
   */
  def create(cluster: Cluster,
             pathPrefix: String,
             asyncAuthenticator: AsyncAuthenticator[String]): ClusterHttpManagement =
    apply(cluster, pathPrefix, asyncAuthenticator)

  /**
   * Creates an instance of [[akka.cluster.http.management.ClusterHttpManagement]] to manage the specified
   * [[akka.cluster.Cluster]] instance. This version provides SSL with the specified ConnectionContext.
   * It does not provide Basic Authentication. It uses the specified path `pathPrefix`.
   */
  def create(cluster: Cluster, pathPrefix: String, https: ConnectionContext) =
    apply(cluster, pathPrefix, https)

  /**
   * Creates an instance of [[akka.cluster.http.management.ClusterHttpManagement]] to manage the specified
   * [[akka.cluster.Cluster]] instance. This version provides Basic Authentication with the specified
   * AsyncAuthenticator. It provide SSL with the specified ConnectionContext. It uses the default path "members".
   */
  def create(cluster: Cluster,
             asyncAuthenticator: AsyncAuthenticator[String],
             https: ConnectionContext): ClusterHttpManagement =
    apply(cluster, asyncAuthenticator, https)

  /**
   * Creates an instance of [[akka.cluster.http.management.ClusterHttpManagement]] to manage the specified
   * [[akka.cluster.Cluster]] instance. This version provides Basic Authentication with the specified
   * AsyncAuthenticator. It provide SSL with the specified ConnectionContext. It uses the specified path `pathPrefix`.
   */
  def create(cluster: Cluster,
             pathPrefix: String,
             asyncAuthenticator: AsyncAuthenticator[String],
             https: ConnectionContext): ClusterHttpManagement =
    apply(cluster, pathPrefix, asyncAuthenticator, https)
}

/**
 * Class to instantiate an [[akka.cluster.http.management.ClusterHttpManagement]] to
 * provide an HTTP management interface for [[akka.cluster.Cluster]].
 */
class ClusterHttpManagement(
    cluster: Cluster,
    pathPrefix: Option[String] = None,
    asyncAuthenticator: Option[AsyncAuthenticator[String]] = None,
    https: Option[ConnectionContext] = None
) {

  private val settings = new ClusterHttpManagementSettings(cluster.system.settings.config)
  private implicit val system = cluster.system
  private implicit val materializer = ActorMaterializer()
  import system.dispatcher

  private val bindingFuture = new AtomicReference[Future[Http.ServerBinding]]()

  def start(): Future[Done] = {
    val serverBindingPromise = Promise[Http.ServerBinding]()
    if (bindingFuture.compareAndSet(null, serverBindingPromise.future)) {
      val clusterHttpManagementRoutes = (pathPrefix, asyncAuthenticator) match {
        case (Some(pp), Some(aa)) ⇒ ClusterHttpManagementRoutes(cluster, pp, aa)
        case (Some(pp), None) ⇒ ClusterHttpManagementRoutes(cluster, pp)
        case (None, Some(aa)) ⇒ ClusterHttpManagementRoutes(cluster, aa)
        case (None, None) ⇒ ClusterHttpManagementRoutes(cluster)
      }

      val routes = RouteResult.route2HandlerFlow(clusterHttpManagementRoutes)

      val serverFutureBinding = https match {
        case Some(context) ⇒
          Http().bindAndHandle(
            routes,
            settings.ClusterHttpManagementHostname,
            settings.ClusterHttpManagementPort,
            connectionContext = context
          )
        case None ⇒
          Http().bindAndHandle(
            routes,
            settings.ClusterHttpManagementHostname,
            settings.ClusterHttpManagementPort
          )
      }

      serverBindingPromise.completeWith(serverFutureBinding)
      serverBindingPromise.future.map(_ => Done)
    } else {
      Future(Done)
    }
  }

  def stop(): Future[Done] =
    if (bindingFuture.get() == null) {
      Future(Done)
    } else {
      val stopFuture = bindingFuture.get().flatMap(_.unbind()).map(_ => Done)
      bindingFuture.set(null)
      stopFuture
    }
}
