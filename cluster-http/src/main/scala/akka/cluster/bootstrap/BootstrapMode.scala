/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.cluster.bootstrap

/**
 * Dictates mode in which cluster bootstraping will be initiated.
 *
 * Generally this will depend on the cloud platform or,
 * available external service discovery services you want to integrate with.
 */
sealed trait BootstrapMode

case object KubernetesDNS extends BootstrapMode

object BootstrapMode {
  lazy val active: Option[BootstrapMode] = decode(Option(System.getenv("BOOTSTRAP_MODE")))

  private[bootstrap] def decode(mode: Option[String]) =
    Some(KubernetesDNS) // FIXME
//    mode match {
//    case Some("kubernetes") => Some(Kubernetes)
//    case _ => None
//  }
}
