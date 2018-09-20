/*
 * Copyright (C) 2017 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.management.http

import java.io.InputStream
import java.security.{ KeyStore, SecureRandom }
import javax.net.ssl.{ KeyManagerFactory, SSLContext, TrustManagerFactory }

import akka.actor.ActorSystem
import akka.http.scaladsl.model.headers.{ Authorization, BasicHttpCredentials }
import akka.http.scaladsl.model.{ HttpRequest, StatusCodes }
import akka.http.scaladsl.server.directives.Credentials
import akka.http.scaladsl.server.{ Directives, Route }
import akka.http.scaladsl.{ ConnectionContext, Http, HttpsConnectionContext }
import akka.management.AkkaManagement
import com.typesafe.config.ConfigFactory
import org.scalatest.{ Matchers, WordSpecLike }

import scala.concurrent.duration._
import scala.concurrent.{ Await, Future }

class HttpManagementEndpointSpecRoutes extends ManagementRouteProvider with Directives {
  override def routes(settings: ManagementRouteProviderSettings): Route =
    complete("hello world")
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
        val configClusterHttpManager = ConfigFactory.parseString(
          """
            |akka.management.http.hostname = "127.0.0.1"
            |akka.management.http.port = 8558
            |akka.management.http.route-providers += "akka.management.http.HttpManagementEndpointSpecRoutes"
          """.stripMargin
        )

        implicit val system = ActorSystem("test", config.withFallback(configClusterHttpManager).resolve())

        val management = AkkaManagement(system)
        management.settings.Http.RouteProviders should contain("akka.management.http.HttpManagementEndpointSpecRoutes")
        management.start()

        val responseFuture = Http().singleRequest(HttpRequest(uri = "http://127.0.0.1:8558/"))
        val response = Await.result(responseFuture, 5.seconds)

        response.status shouldEqual StatusCodes.OK

        try Await.ready(management.stop(), 5.seconds)
        finally system.terminate()
      }

      "setting basic authentication" in {
        val configClusterHttpManager = ConfigFactory.parseString(
          """
            |akka.management.http.hostname = "127.0.0.1"
            |akka.management.http.port = 20000
            |akka.management.http.route-providers += "akka.management.http.HttpManagementEndpointSpecRoutes"
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

        val httpRequest = HttpRequest(uri = "http://127.0.0.1:20000/").addHeader(
            Authorization(BasicHttpCredentials("user", "p4ssw0rd")))
        val responseGetMembersFuture = Http().singleRequest(httpRequest)
        val responseGetMembers = Await.result(responseGetMembersFuture, 5.seconds)

        responseGetMembers.status shouldEqual StatusCodes.OK

        try Await.ready(management.stop(), 5.seconds)
        finally system.terminate()
      }

      "setting ssl" in {
        val configClusterHttpManager = ConfigFactory.parseString(
          """
            |akka.management.http.hostname = "127.0.0.1"
            |akka.management.http.port = 20001
            |akka.management.http.route-providers += "akka.management.http.HttpManagementEndpointSpecRoutes"
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
        val https: HttpsConnectionContext = ConnectionContext.https(sslContext)

        val management = AkkaManagement(system)
        management.setHttpsContext(https)
        management.start()

        val httpRequest = HttpRequest(uri = "https://127.0.0.1:20001/")
        val responseGetMembersFuture = Http().singleRequest(httpRequest, connectionContext = https)
        val responseGetMembers = Await.result(responseGetMembersFuture, 5.seconds)
        responseGetMembers.status shouldEqual StatusCodes.OK

        try Await.ready(management.stop(), 5.seconds)
        finally system.terminate()
      }
    }
  }
}
