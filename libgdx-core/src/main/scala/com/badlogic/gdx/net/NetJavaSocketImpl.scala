package com.badlogic.gdx.net

class NetJavaSocketImpl extends com.badlogic.gdx.net.Socket {
  private var socket: java.net.Socket = null.asInstanceOf[java.net.Socket]
  def this(protocol: com.badlogic.gdx.Net#Protocol, host: java.lang.String, port: scala.Int, hints: com.badlogic.gdx.net.SocketHints) = {
    this()
    try {
      this.socket = new java.net.Socket()
      this.applyHints(hints)
      val address: java.net.InetSocketAddress = new java.net.InetSocketAddress(host, port)
      if (hints != null) {
        this.socket.connect(address, hints.connectTimeout)
      } else {
        this.socket.connect(address)
      }
    } catch {
      case e: java.lang.Exception => {
        throw new com.badlogic.gdx.utils.GdxRuntimeException((("Error making a socket connection to " + host) + ":") + port, e)
      }
    }
  }
  def this(socket: java.net.Socket, hints: com.badlogic.gdx.net.SocketHints) = {
    this()
    this.socket = socket
    this.applyHints(hints)
  }
  private def applyHints(hints: com.badlogic.gdx.net.SocketHints): scala.Unit = {
    if (hints != null) {
      try {
        this.socket.setPerformancePreferences(hints.performancePrefConnectionTime, hints.performancePrefLatency, hints.performancePrefBandwidth)
        this.socket.setTrafficClass(hints.trafficClass)
        this.socket.setTcpNoDelay(hints.tcpNoDelay)
        this.socket.setKeepAlive(hints.keepAlive)
        this.socket.setSendBufferSize(hints.sendBufferSize)
        this.socket.setReceiveBufferSize(hints.receiveBufferSize)
        this.socket.setSoLinger(hints.linger, hints.lingerDuration)
        this.socket.setSoTimeout(hints.socketTimeout)
      } catch {
        case e: java.lang.Exception => {
          throw new com.badlogic.gdx.utils.GdxRuntimeException("Error setting socket hints.", e)
        }
      }
    } else ()
  }
  def isConnected(): scala.Boolean = {
    if (this.socket != null) {
      return this.socket.isConnected()
    } else {
      return false
    }
  }
  def getInputStream(): java.io.InputStream = {
    try {
      return this.socket.getInputStream()
    } catch {
      case e: java.lang.Exception => {
        throw new com.badlogic.gdx.utils.GdxRuntimeException("Error getting input stream from socket.", e)
      }
    }
  }
  def getOutputStream(): java.io.OutputStream = {
    try {
      return this.socket.getOutputStream()
    } catch {
      case e: java.lang.Exception => {
        throw new com.badlogic.gdx.utils.GdxRuntimeException("Error getting output stream from socket.", e)
      }
    }
  }
  def getRemoteAddress(): java.lang.String = {
    return this.socket.getRemoteSocketAddress().toString()
  }
  def dispose(): scala.Unit = {
    if (this.socket != null) {
      try {
        this.socket.close()
        this.socket = null
      } catch {
        case e: java.lang.Exception => {
          throw new com.badlogic.gdx.utils.GdxRuntimeException("Error closing socket.", e)
        }
      }
    } else ()
  }
}