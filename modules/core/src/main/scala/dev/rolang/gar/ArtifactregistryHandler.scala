package dev.rolang.gar

import com.google.api.client.http.{
  ByteArrayContent,
  HttpHeaders,
  HttpRequest,
  HttpRequestFactory,
  HttpResponseException,
}

import java.io.{ByteArrayOutputStream, InputStream, OutputStream}
import java.net.{HttpURLConnection, URL, URLStreamHandler, URLConnection, URLStreamHandlerFactory}
import scala.util.control.NonFatal
import com.google.api.client.http.GenericUrl
import java.io.ByteArrayInputStream
import scala.io.Source
import java.time.OffsetDateTime
import java.security.MessageDigest
import java.nio.charset.StandardCharsets

import com.google.api.client.http.javanet.NetHttpTransport
import com.google.auth.oauth2.GoogleCredentials
import com.google.common.collect.ImmutableList
import com.google.auth.http.{HttpCredentialsAdapter, HttpTransportFactory}
import java.net.MalformedURLException

object ArtifactRegistryUrlHandlerFactory {

  private def loadGoogleCredentials(): GoogleCredentials = {
    val scopes: java.util.Collection[String] =
      ImmutableList.of(
        "https://www.googleapis.com/auth/cloud-platform",
        "https://www.googleapis.com/auth/cloud-platform.read-only",
      )

    GoogleCredentials.getApplicationDefault().createScoped(scopes)
  }

  private final val httpTransportFactory: HttpTransportFactory = { () =>
    new NetHttpTransport()
  }

  private def createHttpRequestFactory(
    credentials: GoogleCredentials
  ): HttpRequestFactory = {
    val requestInitializer = new HttpCredentialsAdapter(credentials)
    val httpTransport      = httpTransportFactory.create()
    httpTransport.createRequestFactory(requestInitializer)
  }

  def createURLStreamHandler(logger: Logger): ArtifactRegistryUrlHandler = {
    logger.info(s"Loading default Google credentials")
    val credentials = loadGoogleCredentials()
    logger.info(s"Google credentials loaded")
    val googleHttpRequestFactory = createHttpRequestFactory(credentials)

    new ArtifactRegistryUrlHandler(googleHttpRequestFactory)(logger)
  }

  def install(logger: Logger) =
    try {
      new URL("artifactregistry://example.com")
      logger.debug(s"The artifactregistry:// URLStreamHandlers are already installed")
    } catch {
      case _: java.net.MalformedURLException =>
        logger.info(s"Installing artifactregistry:// URLStreamHandlers")
        URL.setURLStreamHandlerFactory {
          case p @ "artifactregistry" => createURLStreamHandler(logger)
          case _                      => null
        }

        logger.info(s"Installed artifactregistry:// URLStreamHandler")
    }
}

final case class MavenModule(project: String, region: String, repository: String, domain: String, name: String)

class ArtifactRegistryUrlConnection(
  googleHttpRequestFactory: HttpRequestFactory,
  url: URL,
)(implicit
  logger: Logger
) extends HttpURLConnection(url) {
  private val isMavenMetadata     = url.getPath().endsWith("maven-metadata.xml")
  private val isMavenMetadataSha1 = url.getPath().endsWith("maven-metadata.xml.sha1")

  private val module = (
    url.getPath.split("/").filter(_.nonEmpty),
    url.getHost.split("\\.").headOption.map(_.stripSuffix("-maven")),
  ) match {
    case (Array(project, repo, domain, subDomain, name, _*), Some(region)) =>
      MavenModule(
        project = project,
        region = region,
        repository = repo,
        domain = s"$domain.$subDomain",
        name = s"$name",
      )
    case _ => throw new java.net.MalformedURLException(s"Invalid artifact registry maven url $url")
  }

  private val genericUrl =
    if (isMavenMetadata || isMavenMetadataSha1) {
      val newUrl = new GenericUrl()
      newUrl.setScheme("https")
      newUrl.setHost("content-artifactregistry.googleapis.com")
      newUrl.appendRawPath(
        s"/v1/projects/${module.project}/locations/${module.region}/repositories/${module.repository}/packages/${module.domain}:${module.name}/versions"
      )
      newUrl.put("fields", "versions.name,versions.createTime")
      newUrl.put("pageSize", 1000)
      newUrl
    } else {
      val genericUrl = new GenericUrl()
      genericUrl.setScheme("https")
      genericUrl.setHost(url.getHost)
      genericUrl.appendRawPath(url.getPath)
      genericUrl
    }

  private final var connectedWithHeaders: HttpHeaders = new HttpHeaders()

  override def connect(): Unit = {
    connected = false
    connectedWithHeaders = new HttpHeaders()
    try {
      super.getRequestProperties.forEach { case (header, headerValues) =>
        connectedWithHeaders.set(header, headerValues)
      }

      logger.debug(s"Checking artifact at url: ${url} (mapped to: ${genericUrl}).")

      val httpRequest = googleHttpRequestFactory.buildHeadRequest(genericUrl)
      connected = httpRequest.execute().isSuccessStatusCode
    } catch {
      case ex: HttpResponseException => {
        responseCode = ex.getStatusCode
        responseMessage = ex.getStatusMessage
      }
    }
  }

  override def getInputStream: InputStream = {
    if (!connected) {
      connect()
    }
    try {
      logger.debug(s"Receiving artifact from url: ${genericUrl}.")

      if (isMavenMetadata || isMavenMetadataSha1) {
        val httpRequest  = googleHttpRequestFactory.buildGetRequest(genericUrl)
        val httpResponse = appendHeadersBeforeConnect(httpRequest).execute()

        responseCode = httpResponse.getStatusCode()
        responseMessage = httpResponse.getStatusMessage()

        val resLines = Source.fromInputStream(httpResponse.getContent).getLines()

        val versions = resLines.collect {
          case l if l.contains("\"name\":")       => l.replaceAll("\"|,|\\s", "").split("/").lastOption
          case l if l.contains("\"createTime\":") => Some(l.replace("\"createTime\":", "").replace("\"", "").trim())
          case _                                  => None
        }.collect { case Some(v) => v }.toList
          .grouped(2)
          .map {
            case v :: dt :: Nil => Some((v, OffsetDateTime.parse(dt)))
            case _              => None
          }
          .collect { case Some(v) => v }
          .toList

        val versioningXml = versions.headOption.map(_ => versions.maxBy(_._2)) match {
          case None => ""
          case Some((latestV, latestDt)) =>
            s"""|  <versioning>
                |    <latest>${latestV}</latest>
                |    <release>${latestV}</release>
                |    <versions>
                |      ${versions.map(v => s"<version>${v._1}</version>").mkString("\n      ")}
                |    </versions>
                |    <lastUpdated>${latestDt.toInstant().toEpochMilli()}</lastUpdated>
                |  </versioning>""".stripMargin
        }

        val metadataXml = s"""|<?xml version="1.0" encoding="UTF-8"?>
                              |<metadata>
                              |  <groupId>${module.domain}</groupId>
                              |  <artifactId>${module.name}</artifactId>
                              |$versioningXml
                              |</metadata>""".stripMargin

        logger.debug(s"Received maven-metadata.xml: ${metadataXml}.")

        if (isMavenMetadataSha1) {
          val sha1 =
            MessageDigest
              .getInstance("SHA1")
              .digest(metadataXml.getBytes("UTF-8"))
              .map("%02x".format(_))
              .mkString

          new ByteArrayInputStream(sha1.getBytes())
        } else {
          new ByteArrayInputStream(metadataXml.getBytes(StandardCharsets.UTF_8))
        }
      } else {
        val httpRequest  = googleHttpRequestFactory.buildGetRequest(genericUrl)
        val httpResponse = appendHeadersBeforeConnect(httpRequest).execute()

        responseCode = httpResponse.getStatusCode()
        responseMessage = httpResponse.getStatusMessage()

        httpResponse.getContent
      }
    } catch {
      case ex: HttpResponseException => {
        responseCode = ex.getStatusCode
        responseMessage = ex.getStatusMessage
        null
      }
    }
  }

  override def getOutputStream: OutputStream = {
    if (!connected) {
      connect()
    }
    new ByteArrayOutputStream() {
      override def close(): Unit = {
        super.close()
        try {
          logger.debug(s"Upload artifact from to: ${url}.")

          val httpRequest =
            googleHttpRequestFactory
              .buildPutRequest(
                genericUrl,
                new ByteArrayContent(
                  connectedWithHeaders.getContentType,
                  toByteArray,
                ),
              )

          appendHeadersBeforeConnect(httpRequest).execute()
          ()
        } catch {
          case NonFatal(ex) =>
            logger.error(s"Failed to upload ${url}:\n${ex.getMessage}")
            throw ex
        }
      }
    }
  }

  override def disconnect(): Unit =
    connected = false

  override def usingProxy(): Boolean = false

  private def appendHeadersBeforeConnect(
    httpRequest: HttpRequest
  ): HttpRequest = {
    connectedWithHeaders.forEach { case (header, headerValues) =>
      httpRequest.getHeaders.set(header, headerValues)
    }
    httpRequest
  }

}

class ArtifactRegistryUrlHandler(googleHttpRequestFactory: HttpRequestFactory)(implicit logger: Logger)
    extends URLStreamHandler {

  override def openConnection(url: URL): URLConnection =
    new ArtifactRegistryUrlConnection(googleHttpRequestFactory, url)
}

trait Logger {
  def info(msg: String): Unit
  def error(msg: String): Unit
  def debug(msg: String): Unit
}
