/*
 * Copyright (C) 2017-2018 Lightbend Inc. <http://www.lightbend.com>
 */

package akka.management

import java.io.InputStream
import java.security.KeyStore
import java.security.SecureRandom

import scala.concurrent.duration._
import scala.concurrent.Await
import scala.concurrent.Future

import akka.actor.ActorSystem
import akka.http.javadsl.server.directives.RouteAdapter
import akka.http.scaladsl.model.headers.Authorization
import akka.http.scaladsl.model.headers.BasicHttpCredentials
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.directives.Credentials
import akka.http.scaladsl.ConnectionContext
import akka.http.scaladsl.Http
import akka.http.scaladsl.HttpsConnectionContext
import akka.http.scaladsl.server.Directives
import akka.http.scaladsl.server.Route
import akka.management.scaladsl.AkkaManagement
import akka.management.scaladsl.ManagementRouteProvider
import akka.management.scaladsl.ManagementRouteProviderSettings
import akka.stream.ActorMaterializer
import akka.testkit.SocketUtil
import com.typesafe.config.ConfigFactory
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import org.scalatest.Matchers
import org.scalatest.WordSpecLike

class HttpManagementEndpointSpecRoutesScaladsl extends ManagementRouteProvider with Directives {
  override def routes(settings: ManagementRouteProviderSettings): Route =
    path("scaladsl") {
      get {
        complete("hello Scala")
      }
    }
}

class HttpManagementEndpointSpecRoutesJavadsl extends javadsl.ManagementRouteProvider with Directives {
  override def routes(settings: javadsl.ManagementRouteProviderSettings): akka.http.javadsl.server.Route =
    RouteAdapter {
      path("javadsl") {
        get {
          complete("hello Java")
        }
      }
    }
}

class AkkaManagementHttpEndpointSpec extends WordSpecLike with Matchers {

  val config = ConfigFactory.parseString(
    """
      |akka.remote.log-remote-lifecycle-events = off
      |akka.remote.netty.tcp.port = 0
      |#akka.loglevel = DEBUG
    """.stripMargin
  )

  "Http Cluster Management" should {
    "start and stop" when {
      "not setting any security" in {
        val httpPort = SocketUtil.temporaryLocalPort()
        val configClusterHttpManager = ConfigFactory.parseString(
          s"""
            //#management-host-port
            akka.management.http.hostname = "127.0.0.1"
            akka.management.http.port = 8558
            //#management-host-port
            akka.management.http.port = $httpPort
            akka.management.http.route-providers += "akka.management.HttpManagementEndpointSpecRoutesScaladsl"
            akka.management.http.route-providers += "akka.management.HttpManagementEndpointSpecRoutesJavadsl"
          """
        )

        implicit val system = ActorSystem("test", config.withFallback(configClusterHttpManager).resolve())
        implicit val mat = ActorMaterializer() // needed for toStrict

        val management = AkkaManagement(system)
        management.settings.Http.RouteProviders should contain(
            "akka.management.HttpManagementEndpointSpecRoutesScaladsl")
        management.settings.Http.RouteProviders should contain(
            "akka.management.HttpManagementEndpointSpecRoutesJavadsl")
        management.start()

        val responseFuture1 = Http().singleRequest(HttpRequest(uri = s"http://127.0.0.1:$httpPort/scaladsl"))
        val response1 = Await.result(responseFuture1, 5.seconds)
        response1.status shouldEqual StatusCodes.OK
        Await.result(response1.entity.toStrict(3.seconds, 1000), 3.seconds).data.utf8String should ===("hello Scala")

        val responseFuture2 = Http().singleRequest(HttpRequest(uri = s"http://127.0.0.1:$httpPort/javadsl"))
        val response2 = Await.result(responseFuture2, 5.seconds)
        response2.status shouldEqual StatusCodes.OK
        Await.result(response2.entity.toStrict(3.seconds, 1000), 3.seconds).data.utf8String should ===("hello Java")

        try Await.ready(management.stop(), 5.seconds)
        finally system.terminate()
      }

      "setting basic authentication" in {
        val httpPort = SocketUtil.temporaryLocalPort()
        val configClusterHttpManager = ConfigFactory.parseString(
          s"""
            |akka.management.http.hostname = "127.0.0.1"
            |akka.management.http.port = $httpPort
            |akka.management.http.route-providers += "akka.management.HttpManagementEndpointSpecRoutesScaladsl"
          """.stripMargin
        )

        implicit val system = ActorSystem("test", config.withFallback(configClusterHttpManager).resolve())
        import system.dispatcher

        def myUserPassAuthenticator(credentials: Credentials): Future[Option[String]] =
          credentials match {
            case p @ Credentials.Provided(id) ⇒
              Future {
                // potentially
                if (p.verify("p4ssw0rd")) Some(id)
                else None
              }
            case _ ⇒ Future.successful(None)
          }

        val management = AkkaManagement(system)
        management.setAsyncAuthenticator(myUserPassAuthenticator)
        management.start()

        val httpRequest = HttpRequest(uri = s"http://127.0.0.1:$httpPort/scaladsl").addHeader(
            Authorization(BasicHttpCredentials("user", "p4ssw0rd")))
        val responseGetMembersFuture = Http().singleRequest(httpRequest)
        val responseGetMembers = Await.result(responseGetMembersFuture, 5.seconds)

        responseGetMembers.status shouldEqual StatusCodes.OK

        try Await.ready(management.stop(), 5.seconds)
        finally system.terminate()
      }

      "setting ssl" in {
        val httpPort = SocketUtil.temporaryLocalPort()
        val configClusterHttpManager = ConfigFactory.parseString(
          s"""
            |akka.management.http.hostname = "127.0.0.1"
            |akka.management.http.port = $httpPort
            |akka.management.http.route-providers += "akka.management.HttpManagementEndpointSpecRoutesScaladsl"
            |
            |akka.ssl-config {
            |  loose {
            |    disableSNI = true
            |    disableHostnameVerification = true
            |  }
            |}
          """.stripMargin
        )

        implicit val system = ActorSystem("test", config.withFallback(configClusterHttpManager).resolve())

        val password: Array[Char] = "password".toCharArray // do not store passwords in code, read them from somewhere safe!

        val ks: KeyStore = KeyStore.getInstance("PKCS12")
        val keystore: InputStream = getClass.getClassLoader.getResourceAsStream("httpsDemoKeys/keys/keystore.p12")

        require(keystore != null, "Keystore required!")
        ks.load(keystore, password)

        val keyManagerFactory: KeyManagerFactory = KeyManagerFactory.getInstance("SunX509")
        keyManagerFactory.init(ks, password)

        val tmf: TrustManagerFactory = TrustManagerFactory.getInstance("SunX509")
        tmf.init(ks)

        val sslContext: SSLContext = SSLContext.getInstance("TLS")
        sslContext.init(keyManagerFactory.getKeyManagers, tmf.getTrustManagers, new SecureRandom)
        //#start-akka-management-with-https-context
        val management = AkkaManagement(system)

        val https: HttpsConnectionContext = ConnectionContext.https(sslContext)
        management.setHttpsContext(https)
        management.start()
        //#start-akka-management-with-https-context

        val httpRequest = HttpRequest(uri = s"https://127.0.0.1:$httpPort/scaladsl")
        val responseGetMembersFuture = Http().singleRequest(httpRequest, connectionContext = https)
        val responseGetMembers = Await.result(responseGetMembersFuture, 5.seconds)
        responseGetMembers.status shouldEqual StatusCodes.OK

        try Await.ready(management.stop(), 5.seconds)
        finally system.terminate()
      }
    }
  }
}
