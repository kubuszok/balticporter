package com.badlogic.gdx.net

class ServerSocketHints {
  var backlog: scala.Int = 16
  var performancePrefConnectionTime: scala.Int = 0
  var performancePrefLatency: scala.Int = 1
  var performancePrefBandwidth: scala.Int = 0
  var reuseAddress: scala.Boolean = true
  var acceptTimeout: scala.Int = 5000
  var receiveBufferSize: scala.Int = 4096
}