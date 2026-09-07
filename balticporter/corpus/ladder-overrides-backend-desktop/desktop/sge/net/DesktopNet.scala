/*
 * Port-written replacement for sge's DesktopNet (sge/src/main/scaladesktop/sge/net/DesktopNet.scala):
 * sge routes HTTP through its own SgeHttpClient; the port keeps java's Net surface, so HTTP goes to
 * the core NetJavaImpl as the headless backend does (PROGRESS.md §13.30 step 3, ADJUSTMENTS.tsv).
 */
package sge
package net

import lowlevel.Nullable

class DesktopNet(app: Application) extends sge.Net {
  override def sendHttpRequest(httpRequest: Net.HttpRequest, httpResponseListener: Nullable[Net.HttpResponseListener]): Unit =
    httpResponseListener.foreach(_.failed(utils.SgeError.Unsupported("HTTP requests: the net step drops NetJavaImpl and this port injects no replacement yet")))
  override def cancelHttpRequest(httpRequest: Net.HttpRequest): Unit = ()
  override def isHttpRequestPending(httpRequest: Net.HttpRequest): Boolean = false
  override def newServerSocket(protocol: Net.Protocol, hostname: String, port: Int, hints: ServerSocketHints): ServerSocket =
    new NetJavaServerSocketImpl(protocol, hostname, port, hints)
  override def newServerSocket(protocol: Net.Protocol, port: Int, hints: ServerSocketHints): ServerSocket =
    new NetJavaServerSocketImpl(protocol, port, hints)
  override def newClientSocket(protocol: Net.Protocol, host: String, port: Int, hints: SocketHints): Socket =
    new NetJavaSocketImpl(protocol, host, port, hints)
  override def openURI(URI: String): Boolean = {
    val osName = System.getProperty("os.name", "").toLowerCase
    try {
      val uri = java.net.URI.create(URI).toString
      if (osName.contains("mac")) {
        new ProcessBuilder(DesktopNet.openUriCommand(osName, uri)*).start()
        true
      } else if (java.awt.Desktop.isDesktopSupported && java.awt.Desktop.getDesktop.isSupported(java.awt.Desktop.Action.BROWSE)) {
        java.awt.Desktop.getDesktop.browse(java.net.URI.create(uri))
        true
      } else {
        val command = DesktopNet.openUriCommand(osName, uri)
        if (command.nonEmpty) { new ProcessBuilder(command*).start(); true }
        else { utils.Log.error("DesktopNet: Opening URIs on this environment is not supported. Ignoring."); false }
      }
    } catch {
      case t: Throwable => utils.Log.error("DesktopNet: Failed to open URI. ", t); false
    }
  }
}

object DesktopNet {
  final private[sge] val InternetMaxUrlLength: Int = 2083
  private[sge] def openUriCommand(osName: String, uri: String): List[String] = {
    val os = osName.toLowerCase
    if (os.contains("mac")) List("open", uri)
    else if (os.contains("win")) { if (uri.length > InternetMaxUrlLength) Nil else List("rundll32", "url.dll,FileProtocolHandler", uri) }
    else if (os.contains("linux") || os.contains("nix") || os.contains("nux")) List("xdg-open", uri)
    else Nil
  }
}
