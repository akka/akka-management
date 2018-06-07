/*
 * Copyright (C) 2017 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.discovery.local

import akka.discovery.local.registration.LocalServiceEntry
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

object JsonFormat extends DefaultJsonProtocol {
  implicit val localServiceEntryFormat: RootJsonFormat[LocalServiceEntry] =
    jsonFormat2(LocalServiceEntry)
}
