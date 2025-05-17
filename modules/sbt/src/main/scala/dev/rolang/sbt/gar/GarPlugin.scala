package dev.rolang.sbt.gar

import sbt._
import sbt.Keys._
import java.io.File

import scala.util.{Failure, Success, Try}

object GarPlugin extends AutoPlugin {
  override def trigger = allRequirements

  object autoImport {}

  import autoImport._

  override def projectSettings: Seq[Def.Setting[_]] =
    Seq(
      onLoad in Global := (onLoad in Global).value.andThen { state =>
        val sbtLogger = state.log
        val logger = new dev.rolang.gar.Logger {
          override def info(msg: String): Unit = sbtLogger.info(msg)
          override def error(msg: String): Unit = sbtLogger.err(msg)
          override def debug(msg: String): Unit = sbtLogger.debug(msg)
        }

        Try {
          dev.rolang.gar.ArtifactRegistryUrlHandlerFactory.install(logger)
        } match {
          case Success(_) => state
          case Failure(err) => {
            sbtLogger.err(
              s"Failed to install artigactregistry handler: ${err}. Publishing/resolving artifacts from Google Artifact Registry is disabled."
            )
            state
          }
        }
      },
      csrConfiguration := csrConfiguration.value.withProtocolHandlerDependencies(
        Seq("dev.rolang" % "gar-coursier_2.13" % dev.rolang.gar.version.value)
      )
    ) ++ super.projectSettings
}
