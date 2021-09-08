/*
 * Copyright (C) 2017-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.cluster.http.management.scaladsl

// TODO has to be in akka.cluster because it touches Reachability which is private[akka.cluster]

import scala.collection.immutable._
import scala.concurrent.Promise

import akka.actor.Actor
import akka.actor.ActorSystem
import akka.actor.Address
import akka.actor.ExtendedActorSystem
import akka.actor.Props
import akka.cluster.ClusterEvent.CurrentClusterState
import akka.cluster.InternalClusterAction.LeaderActionsTick
import akka.cluster.MemberStatus.Joining
import akka.cluster.MemberStatus.Up
import akka.cluster._
import akka.cluster.http.management.scaladsl.ClusterHttpManagementRoutesSpec.TestShardedActor
import akka.cluster.sharding.ClusterSharding
import akka.cluster.sharding.ClusterShardingSettings
import akka.cluster.sharding.ShardRegion
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.management.cluster._
import akka.management.cluster.scaladsl.ClusterHttpManagementRoutes
import akka.management.scaladsl.ManagementRouteProviderSettings
import akka.stream.scaladsl.Sink
import akka.util.ByteString
import akka.util.Timeout
import akka.util.Version
import com.typesafe.config.ConfigFactory
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.concurrent.Eventually
import org.scalatest.concurrent.PatienceConfiguration.{ Timeout => ScalatestTimeout }
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.Millis
import org.scalatest.time.Seconds
import org.scalatest.time.Span
import org.scalatest.wordspec.AnyWordSpecLike

class ClusterHttpManagementRoutesSpec
    extends AnyWordSpecLike
    with Matchers
    with ScalatestRouteTest
    with ClusterHttpManagementJsonProtocol
    with ScalaFutures
    with Eventually {

  override implicit def patienceConfig: PatienceConfig =
    PatienceConfig(timeout = Span(3, Seconds), interval = Span(50, Millis))

  val version = new Version("1.42")

  "Http Cluster Management Routes" should {
    "return list of members with cluster leader and oldest" when {
      "calling GET /cluster/members" in {
        val dcName = "one"
        val address1 = Address("akka", "Main", "hostname.com", 3311)
        val address2 = Address("akka", "Main", "hostname2.com", 3311)
        val address3 = Address("akka", "Main", "hostname3.com", 3311)

        val uniqueAddress1 = UniqueAddress(address1, 1L)
        val uniqueAddress2 = UniqueAddress(address2, 2L)

        val clusterMember1 = new Member(uniqueAddress1, 1, Up, Set(s"dc-$dcName"), version)
        val clusterMember2 = new Member(uniqueAddress2, 2, Joining, Set(s"dc-$dcName"), version)
        val currentClusterState =
          CurrentClusterState(SortedSet(clusterMember1, clusterMember2), leader = Some(address1))

        val unreachable = Map(
          UniqueAddress(address3, 2L) -> Set(uniqueAddress1, uniqueAddress2)
        )

        val mockedCluster = mock(classOf[Cluster])
        val mockedClusterReadView = mock(classOf[ClusterReadView])
        val mockedReachability = mock(classOf[Reachability])

        when(mockedCluster.readView).thenReturn(mockedClusterReadView)
        when(mockedCluster.state).thenReturn(currentClusterState)
        when(mockedCluster.selfDataCenter).thenReturn(dcName)
        when(mockedClusterReadView.state).thenReturn(currentClusterState)
        when(mockedClusterReadView.selfAddress).thenReturn(address1)
        when(mockedClusterReadView.leader).thenReturn(Some(address1))
        when(mockedClusterReadView.reachability).thenReturn(mockedReachability)
        when(mockedReachability.observersGroupedByUnreachable).thenReturn(unreachable)

        Get("/cluster/members") ~> ClusterHttpManagementRoutes(mockedCluster) ~> check {
          status shouldEqual StatusCodes.OK
          val clusterUnreachableMember = ClusterUnreachableMember(
            "akka://Main@hostname3.com:3311",
            Seq("akka://Main@hostname.com:3311", "akka://Main@hostname2.com:3311"))
          val clusterMembers = Set(
            ClusterMember("akka://Main@hostname.com:3311", "1", "Up", Set(s"dc-$dcName")),
            ClusterMember("akka://Main@hostname2.com:3311", "2", "Joining", Set(s"dc-$dcName"))
          )

          val expected = ClusterMembers(
            selfNode = s"$address1",
            members = clusterMembers,
            unreachable = Seq(clusterUnreachableMember),
            leader = Some(address1.toString),
            oldest = Some(address1.toString),
            Map(s"dc-$dcName" -> address1.toString)
          )

          val members = responseAs[ClusterMembers]
          // specific checks for easier spotting in failure output what was not matching
          members.leader shouldEqual expected.leader
          members.members shouldEqual expected.members
          members.oldest shouldEqual expected.oldest
          members.selfNode shouldEqual expected.selfNode
          members.unreachable shouldEqual expected.unreachable
          members shouldEqual expected
        }
      }
    }

    "join a member" when {
      "calling POST /cluster/members with form field 'memberAddress'" in {
        val address = "akka.tcp://Main@hostname.com:3311"
        val urlEncodedForm = FormData(Map("address" -> address))

        val mockedCluster = mock(classOf[Cluster])
        doNothing().when(mockedCluster).join(any[Address])

        eventually {
          Post("/cluster/members/", urlEncodedForm) ~> ClusterHttpManagementRoutes(mockedCluster) ~> check {
            status shouldEqual StatusCodes.OK
            responseAs[ClusterHttpManagementMessage] shouldEqual ClusterHttpManagementMessage(s"Joining $address")
          }
        }
      }
    }

    "return information of a member" when {
      def getMember(address1: Address, uri: String) =
        s"calling GET $uri for member $address1" in {
          val uniqueAddress1 = UniqueAddress(address1, 1L)

          val clusterMember1 = Member(uniqueAddress1, Set(), version)

          val members = SortedSet(clusterMember1)

          val mockedCluster = mock(classOf[Cluster])
          val mockedClusterReadView = mock(classOf[ClusterReadView])
          when(mockedCluster.readView).thenReturn(mockedClusterReadView)
          when(mockedClusterReadView.members).thenReturn(members)
          doNothing().when(mockedCluster).leave(any[Address])

          Get(uri) ~> ClusterHttpManagementRoutes(mockedCluster) ~> check {
            status shouldEqual StatusCodes.OK
            responseAs[ClusterMember] shouldEqual ClusterMember(address1.toString, "1", "Joining", Set())
          }
        }

      getMember(Address("akka", "Main", "hostname.com", 3311), "/cluster/members/akka://Main@hostname.com:3311")
      getMember(Address("akka", "Main", "hostname.com", 3311), "/cluster/members/Main@hostname.com:3311")

      getMember(Address("akka", "Main", "[::1]", 3311), "/cluster/members/Main@[::1]:3311")
      getMember(Address("akka", "Main", "[::1]", 3311), "/cluster/members/akka://Main@[::1]:3311")
    }

    "execute leave on a member" when {
      "calling DELETE /cluster/members/akka://Main@hostname.com:3311" in {

        val address1 = Address("akka", "Main", "hostname.com", 3311)
        val address2 = Address("akka", "Main", "hostname2.com", 3311)

        val uniqueAddress1 = UniqueAddress(address1, 1L)
        val uniqueAddress2 = UniqueAddress(address2, 2L)

        val clusterMember1 = Member(uniqueAddress1, Set(), version)
        val clusterMember2 = Member(uniqueAddress2, Set(), version)

        val members = SortedSet(clusterMember1, clusterMember2)

        val mockedCluster = mock(classOf[Cluster])
        val mockedClusterReadView = mock(classOf[ClusterReadView])
        when(mockedCluster.readView).thenReturn(mockedClusterReadView)
        when(mockedClusterReadView.members).thenReturn(members)
        doNothing().when(mockedCluster).leave(any[Address])

        Seq("akka://Main@hostname.com:3311", "Main@hostname.com:3311").foreach(address => {
          Delete(s"/cluster/members/$address") ~> ClusterHttpManagementRoutes(mockedCluster) ~> check {
            status shouldEqual StatusCodes.OK
            responseAs[ClusterHttpManagementMessage] shouldEqual ClusterHttpManagementMessage(s"Leaving $address1")
          }
        })
      }

      "calling PUT /cluster/members/akka://Main@hostname.com:3311 with form field operation LEAVE" in {

        val urlEncodedForm = FormData(Map("operation" -> "leave"))

        val address1 = Address("akka", "Main", "hostname.com", 3311)
        val address2 = Address("akka", "Main", "hostname2.com", 3311)

        val uniqueAddress1 = UniqueAddress(address1, 1L)
        val uniqueAddress2 = UniqueAddress(address2, 2L)

        val clusterMember1 = Member(uniqueAddress1, Set(), version)
        val clusterMember2 = Member(uniqueAddress2, Set(), version)

        val members = SortedSet(clusterMember1, clusterMember2)

        val mockedCluster = mock(classOf[Cluster])
        val mockedClusterReadView = mock(classOf[ClusterReadView])
        when(mockedCluster.readView).thenReturn(mockedClusterReadView)
        when(mockedClusterReadView.members).thenReturn(members)
        doNothing().when(mockedCluster).leave(any[Address])

        Seq("akka://Main@hostname.com:3311", "Main@hostname.com:3311").foreach(address => {
          Put(s"/cluster/members/$address", urlEncodedForm) ~> ClusterHttpManagementRoutes(mockedCluster) ~> check {
            status shouldEqual StatusCodes.OK
            responseAs[ClusterHttpManagementMessage] shouldEqual ClusterHttpManagementMessage(s"Leaving $address1")
          }
        })
      }

      "does not exist and return Not Found" in {
        val address = "akka://Main2@hostname.com:3311"
        val urlEncodedForm = FormData(Map("operation" -> "leave"))

        val address1 = Address("akka", "Main", "hostname.com", 3311)

        val uniqueAddress1 = UniqueAddress(address1, 1L)

        val clusterMember1 = Member(uniqueAddress1, Set(), version)

        val members = SortedSet(clusterMember1)

        val mockedCluster = mock(classOf[Cluster])
        val mockedClusterReadView = mock(classOf[ClusterReadView])
        when(mockedCluster.readView).thenReturn(mockedClusterReadView)
        when(mockedClusterReadView.members).thenReturn(members)
        doNothing().when(mockedCluster).down(any[Address])

        Put(s"/cluster/members/$address", urlEncodedForm) ~> ClusterHttpManagementRoutes(mockedCluster) ~> check {
          status shouldEqual StatusCodes.NotFound
          responseAs[ClusterHttpManagementMessage] shouldEqual ClusterHttpManagementMessage(
            s"Member [$address] not found")
        }
      }
    }

    "execute down on a member" when {
      "calling PUT /cluster/members/akka://Main@hostname.com:3311 with form field operation DOWN" in {

        val urlEncodedForm = FormData(Map("operation" -> "down"))

        val address1 = Address("akka", "Main", "hostname.com", 3311)
        val address2 = Address("akka", "Main", "hostname2.com", 3311)

        val uniqueAddress1 = UniqueAddress(address1, 1L)
        val uniqueAddress2 = UniqueAddress(address2, 2L)

        val clusterMember1 = Member(uniqueAddress1, Set(), version)
        val clusterMember2 = Member(uniqueAddress2, Set(), version)

        val members = SortedSet(clusterMember1, clusterMember2)

        val mockedCluster = mock(classOf[Cluster])
        val mockedClusterReadView = mock(classOf[ClusterReadView])
        when(mockedCluster.readView).thenReturn(mockedClusterReadView)
        when(mockedClusterReadView.members).thenReturn(members)
        doNothing().when(mockedCluster).down(any[Address])

        Seq("akka://Main@hostname.com:3311", "Main@hostname.com:3311").foreach(address => {
          Put(s"/cluster/members/$address", urlEncodedForm) ~> ClusterHttpManagementRoutes(mockedCluster) ~> check {
            status shouldEqual StatusCodes.OK
            responseAs[ClusterHttpManagementMessage] shouldEqual ClusterHttpManagementMessage(s"Downing $address1")
          }
        })
      }

      "does not exist and return Not Found" in {

        val urlEncodedForm = FormData(Map("operation" -> "down"))

        val address1 = Address("akka", "Main", "hostname.com", 3311)

        val uniqueAddress1 = UniqueAddress(address1, 1L)

        val clusterMember1 = Member(uniqueAddress1, Set(), version)

        val members = SortedSet(clusterMember1)

        val mockedCluster = mock(classOf[Cluster])
        val mockedClusterReadView = mock(classOf[ClusterReadView])
        when(mockedCluster.readView).thenReturn(mockedClusterReadView)
        when(mockedClusterReadView.members).thenReturn(members)
        doNothing().when(mockedCluster).down(any[Address])

        Seq("akka://Main2@hostname.com:3311", "Main2@hostname.com:3311").foreach(address => {
          Put(s"/cluster/members/$address", urlEncodedForm) ~> ClusterHttpManagementRoutes(mockedCluster) ~> check {
            status shouldEqual StatusCodes.NotFound
            responseAs[ClusterHttpManagementMessage] shouldEqual ClusterHttpManagementMessage(
              s"Member [$address] not found")
          }
        })
      }
    }

    "return not found operation" when {
      "calling PUT /cluster/members/akka://Main@hostname.com:3311 with form field operation UNKNOWN" in {

        val urlEncodedForm = FormData(Map("operation" -> "unknown"))

        val address1 = Address("akka", "Main", "hostname.com", 3311)

        val uniqueAddress1 = UniqueAddress(address1, 1L)

        val clusterMember1 = Member(uniqueAddress1, Set(), version)

        val members = SortedSet(clusterMember1)

        val mockedCluster = mock(classOf[Cluster])
        val mockedClusterReadView = mock(classOf[ClusterReadView])
        when(mockedCluster.readView).thenReturn(mockedClusterReadView)
        when(mockedClusterReadView.members).thenReturn(members)
        doNothing().when(mockedCluster).down(any[Address])

        Seq("akka://Main@hostname.com:3311", "Main@hostname.com:3311").foreach(address => {
          Put(s"/cluster/members/$address", urlEncodedForm) ~> ClusterHttpManagementRoutes(mockedCluster) ~> check {
            status shouldEqual StatusCodes.BadRequest
            responseAs[ClusterHttpManagementMessage] shouldEqual ClusterHttpManagementMessage("Operation not supported")
          }
        })
      }
    }

    "return shard type names" when {
      "calling GET /cluster/shards" in {
        import scala.concurrent.duration._

        import akka.pattern.ask

        val config = ConfigFactory.parseString(
          """
            |akka.cluster {
            |  auto-down-unreachable-after = 0s
            |  periodic-tasks-initial-delay = 120 seconds // turn off scheduled tasks
            |  publish-stats-interval = 0 s # always, when it happens
            |  failure-detector.implementation-class = akka.cluster.FailureDetectorPuppet
            |  sharding.state-store-mode = ddata
            |}
            |akka.actor.provider = "cluster"
            |akka.remote.log-remote-lifecycle-events = off
            |akka.remote.netty.tcp.port = 0
            |akka.remote.artery.canonical.port = 0
           """.stripMargin
        )
        val configClusterHttpManager = ConfigFactory.parseString(
          """
            |akka.management.http.hostname = "127.0.0.1"
            |akka.management.http.port = 20100
          """.stripMargin
        )

        implicit val system = ActorSystem("test", config.withFallback(configClusterHttpManager))
        val cluster = Cluster(system)
        val selfAddress = system.asInstanceOf[ExtendedActorSystem].provider.getDefaultAddress
        cluster.join(selfAddress)
        cluster.clusterCore ! LeaderActionsTick

        val name = "TestShardRegion"
        val shardRegion = ClusterSharding(system).start(
          name,
          TestShardedActor.props,
          ClusterShardingSettings(system),
          TestShardedActor.extractEntityId,
          TestShardedActor.extractShardId
        )

        implicit val t = ScalatestTimeout(5.seconds)

        shardRegion.ask("hello")(Timeout(3.seconds)).mapTo[String].futureValue(t)

        val clusterHttpManagement = ClusterHttpManagementRouteProvider(system)
        val settings = ManagementRouteProviderSettings(selfBaseUri = "http://127.0.0.1:20100", readOnly = false)
        val binding = Http().newServerAt("127.0.0.1", 20100).bind(clusterHttpManagement.routes(settings)).futureValue

        val responseGetShardEntityTypeKeys =
          Http().singleRequest(HttpRequest(uri = s"http://127.0.0.1:20100/cluster/shards")).futureValue(t)
        responseGetShardEntityTypeKeys.entity.getContentType shouldEqual ContentTypes.`application/json`
        responseGetShardEntityTypeKeys.status shouldEqual StatusCodes.OK
        val unmarshaledGetShardEntityTypeKeys =
          Unmarshal(responseGetShardEntityTypeKeys.entity).to[ShardEntityTypeKeys].futureValue
        unmarshaledGetShardEntityTypeKeys shouldEqual ShardEntityTypeKeys(Set(name))

        binding.unbind().futureValue
        system.terminate()
      }
    }

    "return shard region details" when {

      "calling GET /cluster/shards/{name}" in {
        import scala.concurrent.duration._

        import akka.pattern.ask

        val config = ConfigFactory.parseString(
          """
            |akka.cluster {
            |  auto-down-unreachable-after = 0s
            |  periodic-tasks-initial-delay = 120 seconds // turn off scheduled tasks
            |  publish-stats-interval = 0 s # always, when it happens
            |  failure-detector.implementation-class = akka.cluster.FailureDetectorPuppet
            |  sharding.state-store-mode = ddata
            |}
            |akka.actor.provider = "cluster"
            |akka.remote.log-remote-lifecycle-events = off
            |akka.remote.netty.tcp.port = 0
            |akka.remote.artery.canonical.port = 0
           """.stripMargin
        )
        val configClusterHttpManager = ConfigFactory.parseString(
          """
            |akka.management.http.hostname = "127.0.0.1"
            |akka.management.http.port = 20100
          """.stripMargin
        )

        implicit val system = ActorSystem("test", config.withFallback(configClusterHttpManager))
        val cluster = Cluster(system)
        val selfAddress = system.asInstanceOf[ExtendedActorSystem].provider.getDefaultAddress
        cluster.join(selfAddress)
        cluster.clusterCore ! LeaderActionsTick

        val name = "TestShardRegion"
        val shardRegion = ClusterSharding(system).start(
          name,
          TestShardedActor.props,
          ClusterShardingSettings(system),
          TestShardedActor.extractEntityId,
          TestShardedActor.extractShardId
        )

        implicit val t = ScalatestTimeout(5.seconds)

        shardRegion.ask("hello")(Timeout(3.seconds)).mapTo[String].futureValue(t)

        val clusterHttpManagement = ClusterHttpManagementRouteProvider(system)
        val settings = ManagementRouteProviderSettings(selfBaseUri = "http://127.0.0.1:20100", readOnly = false)
        val binding = Http().newServerAt("127.0.0.1", 20100).bind(clusterHttpManagement.routes(settings)).futureValue

        val responseGetShardDetails =
          Http().singleRequest(HttpRequest(uri = s"http://127.0.0.1:20100/cluster/shards/$name")).futureValue(t)
        responseGetShardDetails.entity.getContentType shouldEqual ContentTypes.`application/json`
        responseGetShardDetails.status shouldEqual StatusCodes.OK
        val unmarshaledGetShardDetails = Unmarshal(responseGetShardDetails.entity).to[ShardDetails].futureValue
        unmarshaledGetShardDetails shouldEqual ShardDetails(Seq(ShardRegionInfo("ShardId", 1)))

        val responseInvalidGetShardDetails = Http()
          .singleRequest(
            HttpRequest(uri = s"http://127.0.0.1:20100/cluster/shards/ThisShardRegionDoesNotExist")
          )
          .futureValue
        responseInvalidGetShardDetails.status shouldEqual StatusCodes.NotFound
        responseInvalidGetShardDetails.entity.getContentType shouldEqual ContentTypes.`application/json`

        binding.unbind().futureValue
        system.terminate()
      }
    }

    "return cluster domain events" when {

      "calling GET /cluster/domain-events" in {

        import scala.concurrent.duration._

        val config = ConfigFactory.parseString(
          """
            |akka.cluster {
            |  auto-down-unreachable-after = 0s
            |  periodic-tasks-initial-delay = 120 seconds // turn off scheduled tasks
            |  publish-stats-interval = 0 s # always, when it happens
            |  failure-detector.implementation-class = akka.cluster.FailureDetectorPuppet
            |}
            |akka.actor.provider = "cluster"
            |akka.remote.log-remote-lifecycle-events = off
            |akka.remote.netty.tcp.port = 0
            |akka.remote.artery.canonical.port = 0
           """.stripMargin
        )
        val configClusterHttpManager = ConfigFactory.parseString(
          """
            |akka.management.http.hostname = "127.0.0.1"
            |akka.management.http.port = 20100
          """.stripMargin
        )

        implicit val system = ActorSystem("test", config.withFallback(configClusterHttpManager))
        val cluster = Cluster(system)
        val selfAddress = system.asInstanceOf[ExtendedActorSystem].provider.getDefaultAddress
        cluster.join(selfAddress)
        cluster.clusterCore ! LeaderActionsTick

        implicit val t = ScalatestTimeout(5.seconds)

        val done = Promise[Unit]
        cluster.registerOnMemberUp { done.success(()) }
        val _ = done.future.futureValue

        val clusterHttpManagement = ClusterHttpManagementRouteProvider(system)
        val settings = ManagementRouteProviderSettings(selfBaseUri = "http://127.0.0.1:20100", readOnly = false)
        val binding = Http().newServerAt("127.0.0.1", 20100).bind(clusterHttpManagement.routes(settings)).futureValue

        val responseGetDomainEvents =
          Http()
            .singleRequest(HttpRequest(uri = s"http://127.0.0.1:20100/cluster/domain-events?type=MemberUp"))
            .futureValue(t)
        responseGetDomainEvents.status shouldEqual StatusCodes.OK
        val responseGetDomainEventsData = responseGetDomainEvents.entity.dataBytes
          .takeWithin(500.millis)
          .fold(ByteString.empty)(_ ++ _)
          .runWith(Sink.head)
          .futureValue
          .utf8String
          .trim

        responseGetDomainEventsData should include("event:MemberUp")

        // TODO: prefer Coordinated shutdown to prevent ubinding the server before the client is
        //  shut down which causes:
        //  java.lang.IllegalStateException: Pool shutdown unexpectedly
        binding.unbind().futureValue
        system.terminate()
      }
    }
  }
}

object ClusterHttpManagementRoutesSpec {

  object TestShardedActor {
    def props: Props = Props(classOf[TestShardedActor])
    def extractShardId: ShardRegion.ExtractShardId = _ => "ShardId"
    def extractEntityId: ShardRegion.ExtractEntityId = {
      case m: Any => ("1", m)
    }
  }
  class TestShardedActor() extends Actor {
    def receive: Receive = {
      case "hello" => sender() ! "world"
    }
  }

}
