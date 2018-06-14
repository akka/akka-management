/*
 * Copyright (C) 2017 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.discovery.local.registration

import java.nio.file.{ Files, Path }

import akka.discovery.local.JsonFormat._
import spray.json._

import scala.collection.immutable.Seq
import scala.io.Source

class LocalServiceRegistration(serviceFile: Path) {

  private val utf8 = "utf-8"

  /**
   * Checks if a <host>:<port> is already registered in the service file
   * @param address the hostname or ip address
   * @param port the port
   * @return true if already registered else false
   */
  def isRegistered(address: String, port: Int): Boolean =
    localServiceEntries.contains(LocalServiceEntry(address, port))

  /**
   * Adds a new <host>:<port> to the service file, if it isn't
   * already registered.
   * @param address the hostname or ip address
   * @param port the port
   */
  def add(address: String, port: Int): Unit = {
    if (!isRegistered(address, port))
      writeToFile(localServiceEntries :+ LocalServiceEntry(address, port))
  }

  /**
   * Removes an <host>:<port> from the service file if registered.
   * @param address the hostname or ip address
   * @param port the port
   */
  def remove(address: String, port: Int): Unit =
    writeToFile(localServiceEntries.filterNot(_ == LocalServiceEntry(address, port)))

  private def writeToFile(localServiceEntries: Seq[LocalServiceEntry]) =
    Files.write(serviceFile, localServiceEntries.toJson.compactPrint.getBytes(utf8))

  def localServiceEntries: Seq[LocalServiceEntry] = {
    if (Files.notExists(serviceFile)) Seq.empty
    else {
      val source = Source.fromFile(serviceFile.toFile, utf8)
      if (source.isEmpty) Seq.empty
      else source.mkString.parseJson.convertTo[Seq[LocalServiceEntry]]
    }
  }

}
