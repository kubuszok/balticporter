package com.badlogic.gdx.scenes.scene2d

class Event extends com.badlogic.gdx.utils.Pool#Poolable {
  private var stage: com.badlogic.gdx.scenes.scene2d.Stage = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.Stage]
  private var targetActor: com.badlogic.gdx.scenes.scene2d.Actor = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.Actor]
  private var listenerActor: com.badlogic.gdx.scenes.scene2d.Actor = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.Actor]
  private var capture: scala.Boolean = false
  private var bubbles: scala.Boolean = true
  private var handled: scala.Boolean = false
  private var stopped: scala.Boolean = false
  private var cancelled: scala.Boolean = false
  def handle(): scala.Unit = {
    this.handled = true
  }
  def cancel(): scala.Unit = {
    this.cancelled = true
    this.stopped = true
    this.handled = true
  }
  def stop(): scala.Unit = {
    this.stopped = true
  }
  def reset(): scala.Unit = {
    this.stage = null
    this.targetActor = null
    this.listenerActor = null
    this.capture = false
    this.bubbles = true
    this.handled = false
    this.stopped = false
    this.cancelled = false
  }
  def getTarget(): com.badlogic.gdx.scenes.scene2d.Actor = {
    return this.targetActor
  }
  def setTarget(targetActor: com.badlogic.gdx.scenes.scene2d.Actor): scala.Unit = {
    this.targetActor = targetActor
  }
  def getListenerActor(): com.badlogic.gdx.scenes.scene2d.Actor = {
    return this.listenerActor
  }
  def setListenerActor(listenerActor: com.badlogic.gdx.scenes.scene2d.Actor): scala.Unit = {
    this.listenerActor = listenerActor
  }
  def getBubbles(): scala.Boolean = {
    return this.bubbles
  }
  def setBubbles(bubbles: scala.Boolean): scala.Unit = {
    this.bubbles = bubbles
  }
  def isHandled(): scala.Boolean = {
    return this.handled
  }
  def isStopped(): scala.Boolean = {
    return this.stopped
  }
  def isCancelled(): scala.Boolean = {
    return this.cancelled
  }
  def setCapture(capture: scala.Boolean): scala.Unit = {
    this.capture = capture
  }
  def isCapture(): scala.Boolean = {
    return this.capture
  }
  def setStage(stage: com.badlogic.gdx.scenes.scene2d.Stage): scala.Unit = {
    this.stage = stage
  }
  def getStage(): com.badlogic.gdx.scenes.scene2d.Stage = {
    return this.stage
  }
}