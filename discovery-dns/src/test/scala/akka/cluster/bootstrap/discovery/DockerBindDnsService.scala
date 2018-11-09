/*
 * Copyright (C) 2017 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.cluster.bootstrap.discovery

import akka.event.LoggingAdapter
import com.spotify.docker.client.DefaultDockerClient
import com.spotify.docker.client.DockerClient.LogsParam
import com.spotify.docker.client.messages.{ ContainerConfig, HostConfig, PortBinding }
import org.scalatest.{ BeforeAndAfterAll, Matchers, Suite }
import org.scalatest.concurrent.Eventually

import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.util.Try
import scala.util.control.NonFatal

trait DockerBindDnsService extends Eventually with BeforeAndAfterAll with Matchers { self: Suite =>
  val client = DefaultDockerClient.fromEnv().build()

  val hostPort: Int

  val log: LoggingAdapter

  var id: Option[String] = None

  def dockerAvailable() = Try(client.ping()).isSuccess

  override def beforeAll(): Unit = {
    super.beforeAll()

    // https://github.com/sameersbn/docker-bind/pull/61
    val image = "raboof/bind:9.11.3-20180713-nochown"
    log.info("Pulling image {}", image)
    try {
      client.pull(image)
    } catch {
      case NonFatal(_) ⇒
        log.warning(s"Failed to pull docker image [$image], is docker running?")
        return
    }
    log.info("Pullled image")

    val containerConfig = ContainerConfig
      .builder()
      .image(image)
      .env("NO_CHOWN=true")
      .hostConfig(
        HostConfig
          .builder()
          .portBindings(
              Map(
                "53/tcp" -> List(PortBinding.of("", hostPort)).asJava,
                "53/udp" -> List(PortBinding.of("", hostPort)).asJava
              ).asJava)
          .binds(HostConfig.Bind
              .from(new java.io.File("discovery-dns/src/test/bind/").getAbsolutePath)
              .to("/data/bind")
              .build())
          .build()
      )
      .build()

    val creation = client.createContainer(containerConfig, "discovery-test-dns-" + getClass.getCanonicalName)
    creation.warnings() should be(null)
    id = Some(creation.id())

    client.startContainer(creation.id())

    eventually(timeout(5.seconds)) {
      client.logs(creation.id(), LogsParam.stderr()).readFully() should include("all zones loaded")
    }

    log.info("Local DNS server running")
  }

  override def afterAll(): Unit = {
    super.afterAll()
    id.foreach(client.killContainer)
    id.foreach(client.removeContainer)
  }
}
