package com.badlogic.gdx.net

class NetJavaServerSocketImpl(protocol$p: com.badlogic.gdx.Net.Protocol, hostname: java.lang.String, port: scala.Int, hints: com.badlogic.gdx.net.ServerSocketHints) extends com.badlogic.gdx.net.ServerSocket {
  private var protocol: com.badlogic.gdx.Net.Protocol = null.asInstanceOf[com.badlogic.gdx.Net.Protocol]
  private var server: java.net.ServerSocket = null.asInstanceOf[java.net.ServerSocket]
  def this(protocol: com.badlogic.gdx.Net.Protocol, port: scala.Int, hints: com.badlogic.gdx.net.ServerSocketHints) = {
    this(protocol, null, port, hints)
  }
  this.protocol = protocol$p
  try {
    this.server = new java.net.ServerSocket()
    if (hints != null) {
      this.server.setPerformancePreferences(hints.performancePrefConnectionTime, hints.performancePrefLatency, hints.performancePrefBandwidth)
      this.server.setReuseAddress(hints.reuseAddress)
      this.server.setSoTimeout(hints.acceptTimeout)
      this.server.setReceiveBufferSize(hints.receiveBufferSize)
    } else ()
    var address: java.net.InetSocketAddress = null.asInstanceOf[java.net.InetSocketAddress]
    if (hostname != null) {
      address = new java.net.InetSocketAddress(hostname, port)
    } else {
      address = new java.net.InetSocketAddress(port)
    }
    if (hints != null) {
      this.server.bind(address, hints.backlog)
    } else {
      this.server.bind(address)
    }
  } catch {
    case e: java.lang.Exception => {
      throw new com.badlogic.gdx.utils.GdxRuntimeException(("Cannot create a server socket at port " + port) + ".", e)
    }
  }
  def getProtocol(): com.badlogic.gdx.Net.Protocol = {
    return this.protocol
  }
  def accept(hints: com.badlogic.gdx.net.SocketHints): com.badlogic.gdx.net.Socket = {
    try {
      return new com.badlogic.gdx.net.NetJavaSocketImpl(this.server.accept(), hints)
    } catch {
      case e: java.lang.Exception => {
        throw new com.badlogic.gdx.utils.GdxRuntimeException("Error accepting socket.", e)
      }
    }
  }
  def dispose(): scala.Unit = {
    if (this.server != null) {
      try {
        this.server.close()
        this.server = null
      } catch {
        case e: java.lang.Exception => {
          throw new com.badlogic.gdx.utils.GdxRuntimeException("Error closing server.", e)
        }
      }
    } else ()
  }
}