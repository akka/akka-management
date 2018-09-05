/*
 * Copyright (C) 2017 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.discovery.marathon.mock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.common.ClasspathFileSource
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.standalone.JsonFileMappingsSource

/**
 * Mock Marathon API working with responses stored in `{marathonResponsesPath}`/`{marathonVersion}`.
 *
 * More information on stubbing can be found in the WireMock docs - http://wiremock.org/docs/stubbing/
 *
 * @param port port to bind to
 * @param marathonResponsesPath response storage location (e.g. `mappings`)
 * @param marathonVersion marathon version (e.g. `marathon_1.6`)
 */
class MarathonApi(
    val port: Int,
    val marathonResponsesPath: String,
    val marathonVersion: String
) {
  private val mappings = new JsonFileMappingsSource(
    new ClasspathFileSource(s"$marathonResponsesPath/$marathonVersion")
  )

  private val wireMockServer = new WireMockServer(
    new WireMockConfiguration().port(port).mappingSource(mappings)
  )

  def start(): Unit = wireMockServer.start()

  def stop(): Unit = wireMockServer.stop()

  def url: String = s"http://localhost:$port"
}
