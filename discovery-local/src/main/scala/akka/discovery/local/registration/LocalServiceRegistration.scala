/*
 * Copyright (C) 2017 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.discovery.local.registration

import java.nio.file.{ Files, Path }

import akka.discovery.local.JsonFormat._
import LocalServiceEntry
import spray.json._

import scala.collection.immutable.Seq
import scala.io.Source

class LocalServiceRegistration(serviceFile: Path) {

  private val utf8 = "utf-8"

  def isRegistered(address: String, port: Int): Boolean =
    localServiceEntries.contains(LocalServiceEntry(address, port))

  def add(address: String, port: Int): Unit =
    writeToFile(localServiceEntries :+ LocalServiceEntry(address, port))

  def remove(address: String, port: Int): Unit =
    writeToFile(localServiceEntries.filterNot(_ == LocalServiceEntry(address, port)))

  private def writeToFile(localServiceEntries: Seq[LocalServiceEntry]) =
    Files.write(serviceFile, localServiceEntries.toJson.compactPrint.getBytes(utf8))

  def localServiceEntries: Seq[LocalServiceEntry] = {
    if (Files.notExists(serviceFile)) Seq.empty
    else
      Source.fromFile(serviceFile.toFile, utf8).mkString.parseJson.convertTo[Seq[LocalServiceEntry]]
  }

}
