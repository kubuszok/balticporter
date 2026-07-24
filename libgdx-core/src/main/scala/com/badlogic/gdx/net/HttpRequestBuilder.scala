package com.badlogic.gdx.net

class HttpRequestBuilder {
  private var httpRequest: com.badlogic.gdx.Net.HttpRequest = null.asInstanceOf[com.badlogic.gdx.Net.HttpRequest]
  def newRequest(): HttpRequestBuilder = {
    if (this.httpRequest != null) {
      throw new java.lang.IllegalStateException("A new request has already been started. Call HttpRequestBuilder.build() first.")
    } else ()
    this.httpRequest = new com.badlogic.gdx.Net.HttpRequest()
    this.httpRequest.setTimeOut(HttpRequestBuilder.defaultTimeout)
    return this
  }
  def method(httpMethod: java.lang.String): HttpRequestBuilder = {
    this.validate()
    this.httpRequest.setMethod(httpMethod)
    return this
  }
  def url(url: java.lang.String): HttpRequestBuilder = {
    this.validate()
    this.httpRequest.setUrl(HttpRequestBuilder.baseUrl + url)
    return this
  }
  def timeout(timeOut: scala.Int): HttpRequestBuilder = {
    this.validate()
    this.httpRequest.setTimeOut(timeOut)
    return this
  }
  def followRedirects(followRedirects: scala.Boolean): HttpRequestBuilder = {
    this.validate()
    this.httpRequest.setFollowRedirects(followRedirects)
    return this
  }
  def includeCredentials(includeCredentials: scala.Boolean): HttpRequestBuilder = {
    this.validate()
    this.httpRequest.setIncludeCredentials(includeCredentials)
    return this
  }
  def header(name: java.lang.String, value: java.lang.String): HttpRequestBuilder = {
    this.validate()
    this.httpRequest.setHeader(name, value)
    return this
  }
  def content(content: java.lang.String): HttpRequestBuilder = {
    this.validate()
    this.httpRequest.setContent(content)
    return this
  }
  def content(contentStream: java.io.InputStream, contentLength: scala.Long): HttpRequestBuilder = {
    this.validate()
    this.httpRequest.setContent(contentStream, contentLength)
    return this
  }
  def formEncodedContent(content: scala.collection.mutable.Map[java.lang.String, java.lang.String]): HttpRequestBuilder = {
    this.validate()
    this.httpRequest.setHeader(com.badlogic.gdx.net.HttpRequestHeader.ContentType, "application/x-www-form-urlencoded")
    val formEncodedContent: java.lang.String = com.badlogic.gdx.net.HttpParametersUtils.convertHttpParameters(content)
    this.httpRequest.setContent(formEncodedContent)
    return this
  }
  def jsonContent(content: java.lang.Object): HttpRequestBuilder = {
    this.validate()
    this.httpRequest.setHeader(com.badlogic.gdx.net.HttpRequestHeader.ContentType, "application/json")
    val jsonContent: java.lang.String = HttpRequestBuilder.json.toJson(content)
    this.httpRequest.setContent(jsonContent)
    return this
  }
  def basicAuthentication(username: java.lang.String, password: java.lang.String): HttpRequestBuilder = {
    this.validate()
    this.httpRequest.setHeader(com.badlogic.gdx.net.HttpRequestHeader.Authorization, "Basic " + com.badlogic.gdx.utils.Base64Coder.encodeString((username + ":") + password))
    return this
  }
  def build(): com.badlogic.gdx.Net.HttpRequest = {
    this.validate()
    val request: com.badlogic.gdx.Net.HttpRequest = this.httpRequest
    this.httpRequest = null
    return request
  }
  private def validate(): scala.Unit = {
    if (this.httpRequest == null) {
      throw new java.lang.IllegalStateException("A new request has not been started yet. Call HttpRequestBuilder.newRequest() first.")
    } else ()
  }
}
object HttpRequestBuilder {
  var baseUrl: java.lang.String = ""
  var defaultTimeout: scala.Int = 1000
  var json: com.badlogic.gdx.utils.Json = new com.badlogic.gdx.utils.Json()
}