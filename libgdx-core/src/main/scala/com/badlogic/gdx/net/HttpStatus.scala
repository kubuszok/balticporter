package com.badlogic.gdx.net

class HttpStatus {
  var statusCode: scala.Int = 0
  def this(statusCode: scala.Int) = {
    this()
    this.statusCode = statusCode
  }
  def getStatusCode(): scala.Int = {
    return this.statusCode
  }
}
object HttpStatus {
  final val SC_CONTINUE: scala.Int = 100
  final val SC_SWITCHING_PROTOCOLS: scala.Int = 101
  final val SC_PROCESSING: scala.Int = 102
  final val SC_OK: scala.Int = 200
  final val SC_CREATED: scala.Int = 201
  final val SC_ACCEPTED: scala.Int = 202
  final val SC_NON_AUTHORITATIVE_INFORMATION: scala.Int = 203
  final val SC_NO_CONTENT: scala.Int = 204
  final val SC_RESET_CONTENT: scala.Int = 205
  final val SC_PARTIAL_CONTENT: scala.Int = 206
  final val SC_MULTI_STATUS: scala.Int = 207
  final val SC_MULTIPLE_CHOICES: scala.Int = 300
  final val SC_MOVED_PERMANENTLY: scala.Int = 301
  final val SC_MOVED_TEMPORARILY: scala.Int = 302
  final val SC_SEE_OTHER: scala.Int = 303
  final val SC_NOT_MODIFIED: scala.Int = 304
  final val SC_USE_PROXY: scala.Int = 305
  final val SC_TEMPORARY_REDIRECT: scala.Int = 307
  final val SC_BAD_REQUEST: scala.Int = 400
  final val SC_UNAUTHORIZED: scala.Int = 401
  final val SC_PAYMENT_REQUIRED: scala.Int = 402
  final val SC_FORBIDDEN: scala.Int = 403
  final val SC_NOT_FOUND: scala.Int = 404
  final val SC_METHOD_NOT_ALLOWED: scala.Int = 405
  final val SC_NOT_ACCEPTABLE: scala.Int = 406
  final val SC_PROXY_AUTHENTICATION_REQUIRED: scala.Int = 407
  final val SC_REQUEST_TIMEOUT: scala.Int = 408
  final val SC_CONFLICT: scala.Int = 409
  final val SC_GONE: scala.Int = 410
  final val SC_LENGTH_REQUIRED: scala.Int = 411
  final val SC_PRECONDITION_FAILED: scala.Int = 412
  final val SC_REQUEST_TOO_LONG: scala.Int = 413
  final val SC_REQUEST_URI_TOO_LONG: scala.Int = 414
  final val SC_UNSUPPORTED_MEDIA_TYPE: scala.Int = 415
  final val SC_REQUESTED_RANGE_NOT_SATISFIABLE: scala.Int = 416
  final val SC_EXPECTATION_FAILED: scala.Int = 417
  final val SC_INSUFFICIENT_SPACE_ON_RESOURCE: scala.Int = 419
  final val SC_METHOD_FAILURE: scala.Int = 420
  final val SC_UNPROCESSABLE_ENTITY: scala.Int = 422
  final val SC_LOCKED: scala.Int = 423
  final val SC_FAILED_DEPENDENCY: scala.Int = 424
  final val SC_INTERNAL_SERVER_ERROR: scala.Int = 500
  final val SC_NOT_IMPLEMENTED: scala.Int = 501
  final val SC_BAD_GATEWAY: scala.Int = 502
  final val SC_SERVICE_UNAVAILABLE: scala.Int = 503
  final val SC_GATEWAY_TIMEOUT: scala.Int = 504
  final val SC_HTTP_VERSION_NOT_SUPPORTED: scala.Int = 505
  final val SC_INSUFFICIENT_STORAGE: scala.Int = 507
}