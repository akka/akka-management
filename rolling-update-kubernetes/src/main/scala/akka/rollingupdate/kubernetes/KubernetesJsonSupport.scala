/*
 * Copyright (C) 2017-2023 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.rollingupdate.kubernetes

import scala.collection.immutable

import akka.annotation.InternalApi
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.DefaultJsonProtocol
import spray.json.JsonFormat
import spray.json.JsObject
import spray.json.RootJsonFormat
import spray.json.JsString
import spray.json.JsValue

/**
 * INTERNAL API
 */
@InternalApi
case class ReplicaAnnotation(revision: String, source: String, otherAnnotations: Map[String, JsValue])

/**
 * INTERNAL API
 */
@InternalApi
case class ReplicaSetMetadata(annotations: ReplicaAnnotation)

/**
 * INTERNAL API
 */
@InternalApi
case class ReplicaSet(metadata: ReplicaSetMetadata)

/**
 * INTERNAL API
 */
@InternalApi
case class PodOwnerRef(name: String, kind: String)

/**
 * INTERNAL API
 */
@InternalApi
case class PodMetadata(ownerReferences: immutable.Seq[PodOwnerRef])

/**
 * INTERNAL API
 */
@InternalApi
case class Pod(metadata: PodMetadata)

/**
 * INTERNAL API
 */
@InternalApi
case class PodCostCustomResource(
    metadata: Metadata,
    spec: Spec,
    kind: String = "PodCost",
    apiVersion: String = "akka.io/v1")

/**
 * INTERNAL API
 */
@InternalApi
case class Metadata(name: String, resourceVersion: Option[String])

/**
 * INTERNAL API
 */
@InternalApi
case class Spec(pods: immutable.Seq[PodCost])

/**
 * INTERNAL API
 */
@InternalApi
trait KubernetesJsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  val _revisionAnnotations: HasRevisionAnnotations = KubernetesJsonSupport.defaultRevisionAnnotations

  // If adding more formats here, remember to also add in META-INF/native-image reflect config
  implicit val metadataFormat: JsonFormat[Metadata] = jsonFormat2(Metadata.apply)
  implicit val podCostFormat: JsonFormat[PodCost] = jsonFormat5(PodCost.apply)
  implicit val specFormat: JsonFormat[Spec] = jsonFormat1(Spec.apply)
  implicit val podCostCustomResourceFormat: RootJsonFormat[PodCostCustomResource] = jsonFormat4(
    PodCostCustomResource.apply)

  implicit val podOwnerRefFormat: JsonFormat[PodOwnerRef] = jsonFormat2(PodOwnerRef.apply)
  implicit val podMetadataFormat: JsonFormat[PodMetadata] = jsonFormat1(PodMetadata.apply)
  implicit val podFormat: RootJsonFormat[Pod] = jsonFormat1(Pod.apply)

  implicit val replicaAnnotationFormat: RootJsonFormat[ReplicaAnnotation] =
    new RootJsonFormat[ReplicaAnnotation] {
      // Not sure if we ever write this out, but if we do, this will let us write out exactly what we took in
      def write(ra: ReplicaAnnotation): JsValue =
        if (ra.revision.nonEmpty && ra.source.nonEmpty) {
          JsObject(ra.otherAnnotations + (ra.source -> JsString(ra.revision)))
        } else JsObject(ra.otherAnnotations)

      def read(json: JsValue): ReplicaAnnotation = {
        json match {
          case JsObject(fields) =>
            _revisionAnnotations.revisionAnnotations.find { annotation =>
              fields.get(annotation).exists(_.isInstanceOf[JsString])
            } match {
              case Some(winningAnnotation) =>
                ReplicaAnnotation(
                  fields(winningAnnotation).asInstanceOf[JsString].value,
                  winningAnnotation,
                  fields - winningAnnotation)

              case None =>
                ReplicaAnnotation("", "", fields)
            }

          case _ => spray.json.deserializationError("expected an object")
        }
      }
    }

  implicit val replicaSetMedatataFormat: JsonFormat[ReplicaSetMetadata] = jsonFormat1(ReplicaSetMetadata.apply)
  implicit val podReplicaSetFormat: RootJsonFormat[ReplicaSet] = jsonFormat1(ReplicaSet.apply)
}

private[kubernetes] object KubernetesJsonSupport {
  val defaultRevisionAnnotations: HasRevisionAnnotations = new HasRevisionAnnotations {
    val revisionAnnotations = Seq("deployment.kubernetes.io/revision")
  }
}
