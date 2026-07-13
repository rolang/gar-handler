package dev.rolang.sbt.gar

import com.google.api.client.http.{ByteArrayContent, GenericUrl, HttpRequestFactory}
import dev.rolang.gar.{ArtifactRegistryUrlHandlerFactory, Logger}
import org.apache.ivy.core.module.descriptor.Artifact
import org.apache.ivy.plugins.repository.{AbstractRepository, Resource}
import org.apache.ivy.plugins.repository.url.URLResource
import org.apache.ivy.plugins.resolver.IBiblioResolver
import org.apache.ivy.util.Message
import sbt.librarymanagement.{RawRepository, Resolver}

import java.io.File
import java.net.URI
import java.nio.file.{Files, StandardCopyOption}

class ArtifactRegistryIvyRepository(logger: Logger) extends AbstractRepository {

  private lazy val requestFactory: HttpRequestFactory =
    ArtifactRegistryUrlHandlerFactory.createRequestFactory(logger)

  override def getName: String = "ArtifactRegistry"

  override def getResource(source: String): Resource =
    new URLResource(URI.create(source).toURL)

  override def get(source: String, destination: File): Unit = {
    val response = requestFactory.buildGetRequest(toHttpsUrl(source)).execute()
    val is = response.getContent
    try Files.copy(is, destination.toPath, StandardCopyOption.REPLACE_EXISTING)
    finally is.close()
  }

  override def put(artifact: Artifact, src: File, destination: String, overwrite: Boolean): Unit = {
    logger.info(s"Uploading artifact to: $destination")
    val bytes = Files.readAllBytes(src.toPath)
    requestFactory
      .buildPutRequest(toHttpsUrl(destination), new ByteArrayContent(null, bytes))
      .execute()
  }

  override def list(parent: String): java.util.List[String] =
    java.util.Collections.emptyList()

  private def toHttpsUrl(raw: String): GenericUrl = {
    val url = URI.create(raw).toURL
    val g = new GenericUrl()
    g.setScheme("https")
    g.setHost(url.getHost)
    g.appendRawPath(url.getPath)
    g
  }
}

object ArtifactRegistryIvyResolver {

  def create(name: String, root: String): Resolver = {
    val logger = new Logger {
      def info(msg: String): Unit = Message.info(msg)
      def error(msg: String): Unit = Message.error(msg)
      def debug(msg: String): Unit = Message.debug(msg)
    }
    val resolver = new IBiblioResolver
    resolver.setName(name)
    resolver.setRoot(root)
    resolver.setM2compatible(true)
    resolver.setUseMavenMetadata(true)
    resolver.setRepository(new ArtifactRegistryIvyRepository(logger))
    new RawRepository(resolver, name)
  }
}
