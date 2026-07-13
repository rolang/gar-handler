package dev.rolang.sbt.gar

import sbt._
import sbt.Keys._

object GarPlugin extends AutoPlugin {
  override def trigger = allRequirements

  object autoImport {}

  import autoImport._

  override def projectSettings = GarCompat.projectSettings
}
