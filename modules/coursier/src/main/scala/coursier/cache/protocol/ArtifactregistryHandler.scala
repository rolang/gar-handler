package coursier.cache.protocol

import java.net.{URLStreamHandler, URLStreamHandlerFactory}
import dev.rolang.gar.{ArtifactRegistryUrlHandlerFactory, Logger}

class ArtifactregistryHandler extends URLStreamHandlerFactory {
  // TODO find a way to inject a logger
  private val logger = new Logger {
    override def info(msg: String): Unit = System.out.println(msg)
    override def error(msg: String): Unit = System.err.println(msg)
    override def debug(msg: String): Unit = ()
  }

  override def createURLStreamHandler(protocol: String): URLStreamHandler =
    ArtifactRegistryUrlHandlerFactory.createURLStreamHandler(logger)
}
