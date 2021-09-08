/*
 * Copyright (C) 2017-2021 Lightbend Inc. <https://www.lightbend.com>
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

import akka.annotation.InternalApi
import akka.pki.pem.DERPrivateKeyLoader
import akka.pki.pem.PEMDecoder
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory

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
  @InternalApi def buildTrustManagers(cacert: Certificate): Array[TrustManager] = {
    val trustStore = KeyStore.getInstance("JKS")
    trustStore.load(null)
    trustStore.setCertificateEntry("cacert", cacert)

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
  @InternalApi def loadCertificate(filename: String): Certificate = blocking {
    val bytes = Files.readAllBytes(new File(filename).toPath)
    certFactory.generateCertificate(new ByteArrayInputStream(bytes))
  }

}
