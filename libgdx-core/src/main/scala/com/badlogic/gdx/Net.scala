package com.badlogic.gdx

trait Net {
  def sendHttpRequest(httpRequest: com.badlogic.gdx.Net.HttpRequest, httpResponseListener: com.badlogic.gdx.Net.HttpResponseListener): scala.Unit
  def cancelHttpRequest(httpRequest: com.badlogic.gdx.Net.HttpRequest): scala.Unit
  def isHttpRequestPending(httpRequest: com.badlogic.gdx.Net.HttpRequest): scala.Boolean
  def newServerSocket(protocol: com.badlogic.gdx.Net.Protocol, hostname: java.lang.String, port: scala.Int, hints: com.badlogic.gdx.net.ServerSocketHints): com.badlogic.gdx.net.ServerSocket
  def newServerSocket(protocol: com.badlogic.gdx.Net.Protocol, port: scala.Int, hints: com.badlogic.gdx.net.ServerSocketHints): com.badlogic.gdx.net.ServerSocket
  def newClientSocket(protocol: com.badlogic.gdx.Net.Protocol, host: java.lang.String, port: scala.Int, hints: com.badlogic.gdx.net.SocketHints): com.badlogic.gdx.net.Socket
  def openURI(URI: java.lang.String): scala.Boolean
}
object Net {
  trait HttpResponse {
    def getResult(): scala.Array[scala.Byte]
    def getResultAsString(): java.lang.String
    def getResultAsStream(): java.io.InputStream
    def getStatus(): com.badlogic.gdx.net.HttpStatus
    def getHeader(name: java.lang.String): java.lang.String
    def getHeaders(): scala.collection.mutable.Map[java.lang.String, scala.collection.mutable.Buffer[java.lang.String]]
  }
  trait HttpMethods
  object HttpMethods {
    final val HEAD: java.lang.String = "HEAD"
    final val GET: java.lang.String = "GET"
    final val POST: java.lang.String = "POST"
    final val PUT: java.lang.String = "PUT"
    final val PATCH: java.lang.String = "PATCH"
    final val DELETE: java.lang.String = "DELETE"
  }
  class HttpRequest extends com.badlogic.gdx.utils.Pool.Poolable {
    private var httpMethod: java.lang.String = null.asInstanceOf[java.lang.String]
    private var url: java.lang.String = null.asInstanceOf[java.lang.String]
    private var headers: scala.collection.mutable.Map[java.lang.String, java.lang.String] = null.asInstanceOf[scala.collection.mutable.Map[java.lang.String, java.lang.String]]
    private var timeOut: scala.Int = 0
    private var content: java.lang.String = null.asInstanceOf[java.lang.String]
    private var contentStream: java.io.InputStream = null.asInstanceOf[java.io.InputStream]
    private var contentLength: scala.Long = 0L
    private var followRedirects: scala.Boolean = true
    private var includeCredentials: scala.Boolean = false
    def this(httpMethod: java.lang.String) = {
      this()
      this.httpMethod = httpMethod
    }
    def this() = {
      this()
      this.headers = new scala.collection.mutable.HashMap[java.lang.String, java.lang.String]()
    }
    def setUrl(url: java.lang.String): scala.Unit = {
      this.url = url
    }
    def setHeader(name: java.lang.String, value: java.lang.String): scala.Unit = {
      this.headers.update(name, value)
    }
    def setContent(content: java.lang.String): scala.Unit = {
      this.content = content
    }
    def setContent(contentStream: java.io.InputStream, contentLength: scala.Long): scala.Unit = {
      this.contentStream = contentStream
      this.contentLength = contentLength
    }
    def setTimeOut(timeOut: scala.Int): scala.Unit = {
      this.timeOut = timeOut
    }
    def setFollowRedirects(followRedirects: scala.Boolean): scala.Unit = {
      if (followRedirects || (com.badlogic.gdx.Gdx.app.getType() != com.badlogic.gdx.Application.ApplicationType.WebGL)) {
        this.followRedirects = followRedirects
      } else {
        throw new java.lang.IllegalArgumentException("Following redirects can't be disabled using the GWT/WebGL backend!")
      }
    }
    def setIncludeCredentials(includeCredentials: scala.Boolean): scala.Unit = {
      this.includeCredentials = includeCredentials
    }
    def setMethod(httpMethod: java.lang.String): scala.Unit = {
      this.httpMethod = httpMethod
    }
    def getTimeOut(): scala.Int = {
      return this.timeOut
    }
    def getMethod(): java.lang.String = {
      return this.httpMethod
    }
    def getUrl(): java.lang.String = {
      return this.url
    }
    def getContent(): java.lang.String = {
      return this.content
    }
    def getContentStream(): java.io.InputStream = {
      return this.contentStream
    }
    def getContentLength(): scala.Long = {
      return this.contentLength
    }
    def getHeaders(): scala.collection.mutable.Map[java.lang.String, java.lang.String] = {
      return this.headers
    }
    def getFollowRedirects(): scala.Boolean = {
      return this.followRedirects
    }
    def getIncludeCredentials(): scala.Boolean = {
      return this.includeCredentials
    }
    def reset(): scala.Unit = {
      this.httpMethod = null
      this.url = null
      this.headers.clear()
      this.timeOut = 0
      this.content = null
      this.contentStream = null
      this.contentLength = 0
      this.followRedirects = true
    }
  }
  trait HttpResponseListener {
    def handleHttpResponse(httpResponse: com.badlogic.gdx.Net.HttpResponse): scala.Unit
    def failed(t: java.lang.Throwable): scala.Unit
    def cancelled(): scala.Unit
  }
  sealed abstract class Protocol
  object Protocol {
    case object TCP extends Protocol
    def values(): Array[Protocol] = Array(TCP)
  }
}