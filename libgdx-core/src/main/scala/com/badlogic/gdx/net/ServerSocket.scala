package com.badlogic.gdx.net

trait ServerSocket extends com.badlogic.gdx.utils.Disposable {
  def getProtocol(): com.badlogic.gdx.Net.Protocol
  def accept(hints: com.badlogic.gdx.net.SocketHints): com.badlogic.gdx.net.Socket
}