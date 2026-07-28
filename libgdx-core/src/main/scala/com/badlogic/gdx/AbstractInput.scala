package com.badlogic.gdx

abstract class AbstractInput extends com.badlogic.gdx.Input {
  var pressedKeys: scala.Array[scala.Boolean] = null.asInstanceOf[scala.Array[scala.Boolean]]
  var justPressedKeys: scala.Array[scala.Boolean] = null.asInstanceOf[scala.Array[scala.Boolean]]
  private final val keysToCatch: com.badlogic.gdx.utils.IntSet = new com.badlogic.gdx.utils.IntSet()
  var pressedKeyCount: scala.Int = 0
  var keyJustPressed: scala.Boolean = false
  this.pressedKeys = new scala.Array[scala.Boolean](com.badlogic.gdx.Input.Keys.MAX_KEYCODE + 1)
  this.justPressedKeys = new scala.Array[scala.Boolean](com.badlogic.gdx.Input.Keys.MAX_KEYCODE + 1)
  @java.lang.Override
  def isKeyPressed(key: scala.Int): scala.Boolean = {
    if (key == com.badlogic.gdx.Input.Keys.ANY_KEY) {
      return this.pressedKeyCount > 0
    } else ()
    if ((key < 0) || (key > com.badlogic.gdx.Input.Keys.MAX_KEYCODE)) {
      return false
    } else ()
    return this.pressedKeys(key)
  }
  @java.lang.Override
  def isKeyJustPressed(key: scala.Int): scala.Boolean = {
    if (key == com.badlogic.gdx.Input.Keys.ANY_KEY) {
      return this.keyJustPressed
    } else ()
    if ((key < 0) || (key > com.badlogic.gdx.Input.Keys.MAX_KEYCODE)) {
      return false
    } else ()
    return this.justPressedKeys(key)
  }
  @java.lang.Override
  def setCatchKey(keycode: scala.Int, catchKey: scala.Boolean): scala.Unit = {
    if (!catchKey) {
      this.keysToCatch.remove(keycode)
    } else {
      this.keysToCatch.add(keycode)
    }
  }
  @java.lang.Override
  def isCatchKey(keycode: scala.Int): scala.Boolean = {
    return this.keysToCatch.contains(keycode)
  }
}
object AbstractInput {
  export com.badlogic.gdx.Input.*
}