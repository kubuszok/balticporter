package com.badlogic.gdx.net

class SocketHints {
  var connectTimeout: scala.Int = 5000
  var performancePrefConnectionTime: scala.Int = 0
  var performancePrefLatency: scala.Int = 1
  var performancePrefBandwidth: scala.Int = 0
  var trafficClass: scala.Int = 20
  var keepAlive: scala.Boolean = true
  var tcpNoDelay: scala.Boolean = true
  var sendBufferSize: scala.Int = 4096
  var receiveBufferSize: scala.Int = 4096
  var linger: scala.Boolean = false
  var lingerDuration: scala.Int = 0
  var socketTimeout: scala.Int = 0
}