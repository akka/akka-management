import sbt.Def
import sbt.Keys._
import sbt._

/*
 * Copyright (C) 2009-2023 Lightbend Inc. <https://www.lightbend.com>
 */
object IntegrationTests {

  def settings: Seq[Def.Setting[_]] = Seq(
    publish / skip := true,
    doc / sources := Seq.empty,
    Test / fork := true
  )
}
