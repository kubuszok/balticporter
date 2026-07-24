package com.badlogic.gdx.scenes.scene2d.actions

class CountdownEventAction[T <: com.badlogic.gdx.scenes.scene2d.Event] extends com.badlogic.gdx.scenes.scene2d.actions.EventAction[T] {
  var count: scala.Int = 0
  var current: scala.Int = 0
  def this(eventClass: java.lang.Class[? <: T], count: scala.Int) = {
    this()
    this.count = count
  }
  def handle(event: T): scala.Boolean = {
    this.current = this.current + 1
    return this.current >= this.count
  }
}