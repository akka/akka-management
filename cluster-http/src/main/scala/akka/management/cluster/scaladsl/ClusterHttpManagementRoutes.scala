/*
 * Copyright (C) 2017-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.management.cluster.scaladsl
import akka.actor.AddressFromURIString
import akka.cluster.sharding.{ ClusterSharding, ShardRegion }
import akka.cluster.{ Cluster, Member, MemberStatus }
import akka.http.scaladsl.model.{ HttpMethod, HttpMethods, StatusCodes, Uri }
import Uri.Path
import akka.http.scaladsl.server.Route
import akka.management.cluster._
import akka.pattern.ask
import akka.pattern.AskTimeoutException
import akka.util.Timeout

import scala.annotation.tailrec
import scala.concurrent.duration._
import scala.util.control.NonFatal

object ClusterHttpManagementRoutes extends ClusterHttpManagementJsonProtocol {
  import ClusterHttpManagementHelper._
  import akka.http.scaladsl.server.Directives._

  private def routeGetMembers(cluster: Cluster): Route =
    get {
      complete {
        val readView = ClusterReadViewAccess.internalReadView(cluster)
        val members = readView.state.members.map(memberToClusterMember)

        val unreachable = readView.reachability.observersGroupedByUnreachable.toVector.sortBy(_._1).map {
          case (subject, observers) =>
            ClusterUnreachableMember(s"${subject.address}", observers.toVector.sorted.map(m => s"${m.address}"))
        }

        val thisDcMembers =
          cluster.state.members.toSeq.filter(node =>
            node.status == MemberStatus.Up && node.dataCenter == cluster.selfDataCenter)

        val leader = readView.leader.map(_.toString)

        val oldest = if (thisDcMembers.isEmpty) None else Some(thisDcMembers.min(Member.ageOrdering).address.toString)

        ClusterMembers(s"${readView.selfAddress}", members, unreachable, leader, oldest, oldestPerRole(thisDcMembers))
      }
    }

  private def routePostMembers(cluster: Cluster): Route =
    post {
      formField('address) { addressString =>
        complete {
          val address = AddressFromURIString(addressString)
          cluster.join(address)
          ClusterHttpManagementMessage(s"Joining $address")
        }
      }
    }

  private def routeGetMember(member: Member): Route =
    get {
      complete {
        memberToClusterMember(member)
      }
    }

  private def routeDeleteMember(cluster: Cluster, member: Member): Route =
    delete {
      complete {
        cluster.leave(member.uniqueAddress.address)
        ClusterHttpManagementMessage(s"Leaving ${member.uniqueAddress.address}")
      }
    }

  private def routePutMember(cluster: Cluster, member: Member) =
    put {
      formField('operation) { operation =>
        ClusterHttpManagementMemberOperation.fromString(operation) match {
          case Some(Down) =>
            cluster.down(member.uniqueAddress.address)
            complete(ClusterHttpManagementMessage(s"Downing ${member.uniqueAddress.address}"))
          case Some(Leave) =>
            cluster.leave(member.uniqueAddress.address)
            complete(ClusterHttpManagementMessage(s"Leaving ${member.uniqueAddress.address}"))
          case _ =>
            complete(StatusCodes.BadRequest -> ClusterHttpManagementMessage("Operation not supported"))
        }
      }
    }

  private def findMember(cluster: Cluster, memberAddress: String): Option[Member] = {
    val readView = ClusterReadViewAccess.internalReadView(cluster)
    readView.members.find(m =>
      s"${m.uniqueAddress.address}" == memberAddress || m.uniqueAddress.address.hostPort == memberAddress)
  }

  private def routeFindMember(cluster: Cluster, readOnly: Boolean): Route = {
    extractMethod { method: HttpMethod =>
      if (readOnly && method != HttpMethods.GET) {
        complete(StatusCodes.MethodNotAllowed)
      } else {
        path(RemainingDecoded) { memberAddress =>
          findMember(cluster, memberAddress) match {
            case Some(member) =>
              routeGetMember(member) ~ routeDeleteMember(cluster, member) ~ routePutMember(cluster, member)
            case None =>
              complete(
                StatusCodes.NotFound -> ClusterHttpManagementMessage(
                  s"Member [$memberAddress] not found"
                )
              )
          }
        }
      }
    }
  }

  private def routeGetClusterDomainEvents(cluster: Cluster) = {
    import akka.actor.ActorRef
    import akka.cluster.ClusterEvent
    import akka.http.scaladsl.marshalling.sse.EventStreamMarshalling._
    import akka.http.scaladsl.model.sse.ServerSentEvent
    import akka.stream.{ Materializer, OverflowStrategy }
    import akka.stream.scaladsl.Source
    import scala.concurrent.{ ExecutionContext, Promise }

    val eventClasses: Map[String, Class[_]] = Map(
      "ClusterDomainEvent" -> classOf[ClusterEvent.ClusterDomainEvent],
      "MemberEvent" -> classOf[ClusterEvent.MemberEvent],
      "MemberJoined" -> classOf[ClusterEvent.MemberJoined],
      "MemberWeaklyUp" -> classOf[ClusterEvent.MemberWeaklyUp],
      "MemberUp" -> classOf[ClusterEvent.MemberUp],
      "MemberLeft" -> classOf[ClusterEvent.MemberLeft],
      "MemberExited" -> classOf[ClusterEvent.MemberExited],
      "MemberDowned" -> classOf[ClusterEvent.MemberDowned],
      "MemberRemoved" -> classOf[ClusterEvent.MemberRemoved],
      "LeaderChanged" -> classOf[ClusterEvent.LeaderChanged],
      "RoleLeaderChanged" -> classOf[ClusterEvent.RoleLeaderChanged],
      "ClusterShuttingDown" -> ClusterEvent.ClusterShuttingDown.getClass,
      "ReachabilityEvent" -> classOf[ClusterEvent.ReachabilityEvent],
      "UnreachableMember" -> classOf[ClusterEvent.UnreachableMember],
      "ReachableMember" -> classOf[ClusterEvent.ReachableMember],
      "DataCenterReachabilityEvent" -> classOf[ClusterEvent.DataCenterReachabilityEvent],
      "UnreachableDataCenter" -> classOf[ClusterEvent.UnreachableDataCenter],
      "ReachableDataCenter" -> classOf[ClusterEvent.ReachableDataCenter]
    )

    extractMaterializer { implicit mat: Materializer =>
      implicit val ec: ExecutionContext = mat.executionContext

      get {
        parameter("type".as[String].*) { providedEventTypes =>
          val classes =
            if (providedEventTypes.nonEmpty)
              providedEventTypes.foldLeft(List.empty[Class[_]]) {
                case (accum, eventType) =>
                  eventClasses.get(eventType).toList ::: accum
              }
            else
              List(classOf[ClusterEvent.ClusterDomainEvent])

          val eventualActorRef = Promise[Option[ActorRef]]

          val clusterEvents = Source
            .actorRef[ClusterEvent.ClusterDomainEvent](
              completionMatcher = PartialFunction.empty,
              failureMatcher = PartialFunction.empty,
              bufferSize = 128,
              overflowStrategy = OverflowStrategy.fail
            )
            .map(ClusterDomainEventServerSentEventEncoder.encode)
            .collect {
              case Some(serverSentEvent) => serverSentEvent
            }
            .keepAlive(10.seconds, () => ServerSentEvent.heartbeat)
            .mapMaterializedValue { actorRef =>
              eventualActorRef.success(Some(actorRef))
              ()
            }
            .watchTermination() {
              case (_, eventualDone) =>
                eventualDone.onComplete { _ =>
                  // the stream has terminated, so complete the promise if it isn't already, and
                  // then unsubscribe if previously subscribed

                  val _ = eventualActorRef.trySuccess(None)

                  eventualActorRef.future.foreach {
                    case Some(actorRef) =>
                      if (classes.nonEmpty) {
                        cluster.unsubscribe(actorRef)
                      }

                    case None =>
                  }
                }
            }

          eventualActorRef.future.foreach {
            case Some(actorRef) =>
              if (classes.nonEmpty) {
                cluster.subscribe(
                  actorRef,
                  initialStateMode = ClusterEvent.InitialStateAsEvents,
                  classes: _*
                )
              }

            case None =>
          }

          complete(clusterEvents)
        }

      }
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
                ShardDetails(shardRegionStats.stats.map(s => ShardRegionInfo(s._1, s._2)).toVector)
              }
          } catch {
            case _: AskTimeoutException =>
              StatusCodes.NotFound -> ClusterHttpManagementMessage(
                s"Shard Region $shardRegionName not responding, may have been terminated")
            case _: IllegalArgumentException => // Akka 2.5
              StatusCodes.NotFound -> ClusterHttpManagementMessage(s"Shard Region $shardRegionName is not started")
            case _: IllegalStateException => // Akka 2.6
              StatusCodes.NotFound -> ClusterHttpManagementMessage(s"Shard Region $shardRegionName is not started")
          }
        }
      }
    }

  /**
   * Creates an instance of [[ClusterHttpManagementRoutes]] to manage the specified
   * [[akka.cluster.Cluster]] instance. This version does not provide Basic Authentication.
   */
  def apply(cluster: Cluster): Route =
    pathPrefix("cluster") {
      concat(
        pathEndOrSingleSlash {
          routePutCluster(cluster)
        },
        pathPrefix("members") {
          concat(
            pathEndOrSingleSlash {
              routeGetMembers(cluster) ~ routePostMembers(cluster)
            },
            routeFindMember(cluster, readOnly = false)
          )
        },
        pathPrefix"domain-events") {
          routeGetClusterDomainEvents(cluster)
        },
        pathPrefix("shards" / Remaining) { shardRegionName =>
          routeGetShardInfo(cluster, shardRegionName)
        }
      )
    }

  private def routePutCluster(cluster: Cluster): Route = {
    put {
      formField('operation) { operation =>
        if (operation.toLowerCase == "prepare-for-full-shutdown") {
          try {
            val m = cluster.getClass.getMethod("prepareForFullClusterShutdown")
            m.invoke(cluster)
            complete(ClusterHttpManagementMessage(s"Preparing for full cluster shutdown"))
          } catch {
            case NonFatal(_) =>
              complete(StatusCodes.BadRequest, "prepare-for-full-shutdown not supported in this Akka version")
          }
        } else {
          complete(StatusCodes.BadRequest -> ClusterHttpManagementMessage("Operation not supported"))
        }
      }
    }
  }

  /**
   * Creates an instance of [[ClusterHttpManagementRoutes]] with only the read only routes.
   */
  def readOnly(cluster: Cluster): Route = {
    concat(
      pathPrefix("cluster" / "members") {
        concat(pathEndOrSingleSlash {
          routeGetMembers(cluster)
        }, routeFindMember(cluster, readOnly = true))
      },
      pathPrefix("cluster" / "domain-events") {
        routeGetClusterDomainEvents(cluster)
      },
      pathPrefix("cluster" / "shards" / Remaining) { shardRegionName =>
        routeGetShardInfo(cluster, shardRegionName)
      }
    )
  }

  /**
   *  A special version of Remaining that returns the remaining decoded (while Remaining uses path.toString which encodes
   *  where necessary.
   */
  private lazy val RemainingDecoded = RemainingPath.map { path =>
    @tailrec
    def decoded(path: Uri.Path, current: StringBuilder): String =
      path match {
        case Path.Slash(next)         => decoded(next, current += '/')
        case Path.Segment(head, tail) => decoded(tail, current ++= head)
        case Path.Empty               => current.result()
      }

    decoded(path, new StringBuilder)
  }
}
