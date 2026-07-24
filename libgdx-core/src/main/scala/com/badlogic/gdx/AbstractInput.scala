package com.badlogic.gdx

abstract class AbstractInput extends com.badlogic.gdx.Input {
  protected var pressedKeys: scala.Array[scala.Boolean] = null.asInstanceOf[scala.Array[scala.Boolean]]
  protected var justPressedKeys: scala.Array[scala.Boolean] = null.asInstanceOf[scala.Array[scala.Boolean]]
  private final val keysToCatch: com.badlogic.gdx.utils.IntSet = new com.badlogic.gdx.utils.IntSet()
  protected var pressedKeyCount: scala.Int = 0
  protected var keyJustPressed: scala.Boolean = false
  def this() = {
    this()
    this.pressedKeys = new Array[scala.Boolean](com.badlogic.gdx.Input.Keys.MAX_KEYCODE + 1)
    this.justPressedKeys = new Array[scala.Boolean](com.badlogic.gdx.Input.Keys.MAX_KEYCODE + 1)
  }
  def isKeyPressed(key: scala.Int): scala.Boolean = {
    if (key == com.badlogic.gdx.Input.Keys.ANY_KEY) {
      return this.pressedKeyCount > 0
    } else ()
    if ((key < 0) || (key > com.badlogic.gdx.Input.Keys.MAX_KEYCODE)) {
      return false
    } else ()
    return this.pressedKeys(key)
  }
  def isKeyJustPressed(key: scala.Int): scala.Boolean = {
    if (key == com.badlogic.gdx.Input.Keys.ANY_KEY) {
      return this.keyJustPressed
    } else ()
    if ((key < 0) || (key > com.badlogic.gdx.Input.Keys.MAX_KEYCODE)) {
      return false
    } else ()
    return this.justPressedKeys(key)
  }
  def setCatchKey(keycode: scala.Int, catchKey: scala.Boolean): scala.Unit = {
    if (!catchKey) {
      this.keysToCatch.remove(keycode)
    } else {
      this.keysToCatch.add(keycode)
    }
  }
  def isCatchKey(keycode: scala.Int): scala.Boolean = {
    return this.keysToCatch.contains(keycode)
  }
}