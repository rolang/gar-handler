package dev.rolang.sbt.gar

import sbt._
import sbt.Keys._
import scala.util.{Failure, Success, Try}

object GarCompat {
  def projectSettings: Seq[Def.Setting[_]] =
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
          case Success(_)   => state
          case Failure(err) => {
            sbtLogger.err(
              s"Failed to install artifactregistry handler: ${err}. Publishing/resolving artifacts from Google Artifact Registry is disabled."
            )
            state
          }
        }
      },
      csrConfiguration := csrConfiguration.value.withProtocolHandlerDependencies(
        Seq("dev.rolang" % "gar-coursier_2.13" % dev.rolang.gar.version.value)
      ),
      publishTo := publishTo.value.map {
        case m: sbt.librarymanagement.MavenRepository if m.root.startsWith("artifactregistry://") =>
          ArtifactRegistryIvyResolver.create(m.name, m.root)
        case other => other
      }
    )
}
