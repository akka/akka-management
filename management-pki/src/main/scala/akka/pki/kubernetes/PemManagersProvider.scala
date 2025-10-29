/*
 * Copyright (C) 2017-2025 Lightbend Inc. <https://akka.io>
 */

package akka.pki.kubernetes

import java.io.ByteArrayInputStream
import java.io.File
import java.nio.charset.Charset
import java.nio.file.Files
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.Certificate
import java.security.cert.CertificateFactory

import scala.concurrent.blocking
import scala.jdk.CollectionConverters._

import akka.annotation.InternalApi
import akka.pki.pem.DERPrivateKeyLoader
import akka.pki.pem.PEMDecoder
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import scala.util.Random

/**
 * INTERNAL API
 * Convenience methods to ease building an SSLContext from k8s-provided PEM files.
 */
// Duplicate from https://github.com/akka/akka/blob/31f654768f86db68f4c22daa2cbd0bae28fc1fad/akka-remote/src/main/scala/akka/remote/artery/tcp/ssl/PemManagersProvider.scala#L35
// Eventually that will be a bit more open and we can reuse the class from akka in akka-management.
// See also https://github.com/akka/akka-http/issues/3772
@InternalApi
private[akka] object PemManagersProvider {

  /**
   * INTERNAL API
   */
  @InternalApi def buildTrustManagers(cacerts: Iterable[Certificate]): Array[TrustManager] = {
    val trustStore = KeyStore.getInstance("JKS")
    trustStore.load(null)
    cacerts.foreach(cert => trustStore.setCertificateEntry("cacert-" + Random.alphanumeric.take(6).mkString(""), cert))

    val tmf =
      TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm)
    tmf.init(trustStore)
    tmf.getTrustManagers
  }

  /**
   * INTERNAL API
   */
  @InternalApi def loadPrivateKey(filename: String): PrivateKey = blocking {
    val bytes = Files.readAllBytes(new File(filename).toPath)
    val pemData = new String(bytes, Charset.forName("UTF-8"))
    DERPrivateKeyLoader.load(PEMDecoder.decode(pemData))
  }

  private val certFactory = CertificateFactory.getInstance("X.509")

  /**
   * INTERNAL API
   */
  @InternalApi def loadCertificates(filename: String): Iterable[Certificate] = blocking {
    val bytes = Files.readAllBytes(new File(filename).toPath)
    certFactory.generateCertificates(new ByteArrayInputStream(bytes)).asScala
  }

}
