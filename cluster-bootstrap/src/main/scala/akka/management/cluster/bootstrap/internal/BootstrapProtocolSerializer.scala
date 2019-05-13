/*
 * Copyright (C) 2017-2018 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.management.cluster.bootstrap.internal

import java.io.NotSerializableException

import akka.actor.ExtendedActorSystem
import akka.management.cluster.bootstrap.contactpoint.HttpBootstrapJsonProtocol.SeedNodes
import akka.management.cluster.bootstrap.contactpoint.HttpBootstrapJsonProtocol
import akka.management.cluster.bootstrap.internal.RemotingContactPoint.GetSeedNodes
import akka.serialization.{BaseSerializer, SerializerWithStringManifest}
import spray.json._

class BootstrapProtocolSerializer(val system: ExtendedActorSystem) extends SerializerWithStringManifest
  with BaseSerializer with HttpBootstrapJsonProtocol {

  override def toBinary(obj: AnyRef): Array[Byte] = obj match {
    case seedNodes: SeedNodes => seedNodes.toJson.compactPrint.getBytes("utf-8")
    case GetSeedNodes => Array.emptyByteArray
  }

  override def manifest(obj: AnyRef): String = obj match {
    case _: SeedNodes => "A"
    case GetSeedNodes => "B"
    case _ => throw new IllegalArgumentException(s"Can't serialize object of type ${obj.getClass} in [${getClass.getName}]")
  }

  override def fromBinary(bytes: Array[Byte], manifest: String): AnyRef = manifest match {
    case "A" => new String(bytes, "utf-8").parseJson.convertTo[SeedNodes]
    case "B" => GetSeedNodes
    case _ => throw new NotSerializableException(
      s"Unimplemented deserialization of message with manifest [$manifest] in [${getClass.getName}]")

  }
}
