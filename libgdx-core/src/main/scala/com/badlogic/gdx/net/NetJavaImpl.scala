package com.badlogic.gdx.net

class NetJavaImpl(maxThreads: scala.Int) {
  private var executorService: java.util.concurrent.ThreadPoolExecutor = null.asInstanceOf[java.util.concurrent.ThreadPoolExecutor]
  var connections: com.badlogic.gdx.utils.ObjectMap[com.badlogic.gdx.Net.HttpRequest, java.net.HttpURLConnection] = null.asInstanceOf[com.badlogic.gdx.utils.ObjectMap[com.badlogic.gdx.Net.HttpRequest, java.net.HttpURLConnection]]
  var listeners: com.badlogic.gdx.utils.ObjectMap[com.badlogic.gdx.Net.HttpRequest, com.badlogic.gdx.Net.HttpResponseListener] = null.asInstanceOf[com.badlogic.gdx.utils.ObjectMap[com.badlogic.gdx.Net.HttpRequest, com.badlogic.gdx.Net.HttpResponseListener]]
  var tasks: com.badlogic.gdx.utils.ObjectMap[com.badlogic.gdx.Net.HttpRequest, java.util.concurrent.Future[?]] = null.asInstanceOf[com.badlogic.gdx.utils.ObjectMap[com.badlogic.gdx.Net.HttpRequest, java.util.concurrent.Future[?]]]
  val isCachedPool: scala.Boolean = maxThreads == java.lang.Integer.MAX_VALUE
  def this() = {
    this(java.lang.Integer.MAX_VALUE)
  }
  this.executorService = new java.util.concurrent.ThreadPoolExecutor(if (isCachedPool) 0 else maxThreads, maxThreads, 60L, java.util.concurrent.TimeUnit.SECONDS, if (isCachedPool) new java.util.concurrent.SynchronousQueue[java.lang.Runnable]() else new java.util.concurrent.LinkedBlockingQueue[java.lang.Runnable](), new java.util.concurrent.ThreadFactory())
  this.executorService.allowCoreThreadTimeOut(!isCachedPool)
  this.connections = new com.badlogic.gdx.utils.ObjectMap[com.badlogic.gdx.Net.HttpRequest, java.net.HttpURLConnection]()
  this.listeners = new com.badlogic.gdx.utils.ObjectMap[com.badlogic.gdx.Net.HttpRequest, com.badlogic.gdx.Net.HttpResponseListener]()
  this.tasks = new com.badlogic.gdx.utils.ObjectMap[com.badlogic.gdx.Net.HttpRequest, java.util.concurrent.Future[?]]()
  def sendHttpRequest(httpRequest: com.badlogic.gdx.Net.HttpRequest, httpResponseListener: com.badlogic.gdx.Net.HttpResponseListener): scala.Unit = {
    if (httpRequest.getUrl() == null) {
      httpResponseListener.failed(new com.badlogic.gdx.utils.GdxRuntimeException("can't process a HTTP request without URL set"))
      return
    } else ()
    try {
      val method: java.lang.String = httpRequest.getMethod()
      var url: java.net.URL = null.asInstanceOf[java.net.URL]
      val doInput: scala.Boolean = !method.equalsIgnoreCase(com.badlogic.gdx.Net.HttpMethods.HEAD)
      val doingOutPut: scala.Boolean = (method.equalsIgnoreCase(com.badlogic.gdx.Net.HttpMethods.POST) || method.equalsIgnoreCase(com.badlogic.gdx.Net.HttpMethods.PUT)) || method.equalsIgnoreCase(com.badlogic.gdx.Net.HttpMethods.PATCH)
      if (method.equalsIgnoreCase(com.badlogic.gdx.Net.HttpMethods.GET) || method.equalsIgnoreCase(com.badlogic.gdx.Net.HttpMethods.HEAD)) {
        var queryString: java.lang.String = ""
        val value: java.lang.String = httpRequest.getContent()
        if ((value != null) && (!"".equals(value))) {
          queryString = "?" + value
        } else ()
        url = new java.net.URL(httpRequest.getUrl() + queryString)
      } else {
        url = new java.net.URL(httpRequest.getUrl())
      }
      val connection: java.net.HttpURLConnection = url.openConnection().asInstanceOf[java.net.HttpURLConnection]
      connection.setDoOutput(doingOutPut)
      connection.setDoInput(doInput)
      connection.setRequestMethod(method)
      java.net.HttpURLConnection.setFollowRedirects(httpRequest.getFollowRedirects())
      this.putIntoConnectionsAndListeners(httpRequest, httpResponseListener, connection)
      for (header <- httpRequest.getHeaders()) {
        connection.addRequestProperty(header._1, header._2)
      }
      connection.setConnectTimeout(httpRequest.getTimeOut())
      connection.setReadTimeout(httpRequest.getTimeOut())
      this.tasks.put(httpRequest, this.executorService.submit(new java.lang.Runnable()))
    } catch {
      case e: java.lang.Exception => {
        try {
          httpResponseListener.failed(e)
        } finally {
          this.removeFromConnectionsAndListeners(httpRequest)
        }
        return
      }
    }
  }
  def cancelHttpRequest(httpRequest: com.badlogic.gdx.Net.HttpRequest): scala.Unit = {
    val httpResponseListener: com.badlogic.gdx.Net.HttpResponseListener = this.getFromListeners(httpRequest)
    if (httpResponseListener != null) {
      httpResponseListener.cancelled()
      this.cancelTask(httpRequest)
      this.removeFromConnectionsAndListeners(httpRequest)
    } else ()
  }
  def isHttpRequestPending(httpRequest: com.badlogic.gdx.Net.HttpRequest): scala.Boolean = {
    return this.getFromListeners(httpRequest) != null
  }
  private def cancelTask(httpRequest: com.badlogic.gdx.Net.HttpRequest): scala.Unit = {
    val task: java.util.concurrent.Future[?] = this.tasks.get(httpRequest)
    if (task != null) {
      task.cancel(false)
    } else ()
  }
  def removeFromConnectionsAndListeners(httpRequest: com.badlogic.gdx.Net.HttpRequest): scala.Unit = {
    this.connections.remove(httpRequest)
    this.listeners.remove(httpRequest)
    this.tasks.remove(httpRequest)
  }
  def putIntoConnectionsAndListeners(httpRequest: com.badlogic.gdx.Net.HttpRequest, httpResponseListener: com.badlogic.gdx.Net.HttpResponseListener, connection: java.net.HttpURLConnection): scala.Unit = {
    this.connections.put(httpRequest, connection)
    this.listeners.put(httpRequest, httpResponseListener)
  }
  def getFromListeners(httpRequest: com.badlogic.gdx.Net.HttpRequest): com.badlogic.gdx.Net.HttpResponseListener = {
    val httpResponseListener: com.badlogic.gdx.Net.HttpResponseListener = this.listeners.get(httpRequest)
    return httpResponseListener
  }
}
object NetJavaImpl {
  class HttpClientResponse(connection$p: java.net.HttpURLConnection) extends com.badlogic.gdx.Net.HttpResponse {
    private var connection: java.net.HttpURLConnection = null.asInstanceOf[java.net.HttpURLConnection]
    private var status: com.badlogic.gdx.net.HttpStatus = null.asInstanceOf[com.badlogic.gdx.net.HttpStatus]
    this.connection = connection$p
    try {
      this.status = new com.badlogic.gdx.net.HttpStatus(connection$p.getResponseCode())
    } catch {
      case e: java.io.IOException => {
        this.status = new com.badlogic.gdx.net.HttpStatus(-1)
      }
    }
    def getResult(): scala.Array[scala.Byte] = {
      val input: java.io.InputStream = this.getInputStream()
      if (input == null) {
        return com.badlogic.gdx.utils.StreamUtils.EMPTY_BYTES
      } else ()
      try {
        return com.badlogic.gdx.utils.StreamUtils.copyStreamToByteArray(input, this.connection.getContentLength())
      } catch {
        case e: java.io.IOException => {
          return com.badlogic.gdx.utils.StreamUtils.EMPTY_BYTES
        }
      } finally {
        com.badlogic.gdx.utils.StreamUtils.closeQuietly(input)
      }
    }
    def getResultAsString(): java.lang.String = {
      val input: java.io.InputStream = this.getInputStream()
      if (input == null) {
        return ""
      } else ()
      try {
        return com.badlogic.gdx.utils.StreamUtils.copyStreamToString(input, this.connection.getContentLength(), "UTF8")
      } catch {
        case e: java.io.IOException => {
          return ""
        }
      } finally {
        com.badlogic.gdx.utils.StreamUtils.closeQuietly(input)
      }
    }
    def getResultAsStream(): java.io.InputStream = {
      return this.getInputStream()
    }
    def getStatus(): com.badlogic.gdx.net.HttpStatus = {
      return this.status
    }
    def getHeader(name: java.lang.String): java.lang.String = {
      return this.connection.getHeaderField(name)
    }
    def getHeaders(): scala.collection.mutable.Map[java.lang.String, scala.collection.mutable.Buffer[java.lang.String]] = {
      return this.connection.getHeaderFields()
    }
    private def getInputStream(): java.io.InputStream = {
      try {
        return this.connection.getInputStream()
      } catch {
        case e: java.io.IOException => {
          return this.connection.getErrorStream()
        }
      }
    }
  }
}