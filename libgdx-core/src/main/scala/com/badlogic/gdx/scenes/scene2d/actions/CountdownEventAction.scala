package com.badlogic.gdx.scenes.scene2d.actions

class CountdownEventAction[T <: com.badlogic.gdx.scenes.scene2d.Event](eventClass$p: java.lang.Class[? <: T], count$p: scala.Int) extends com.badlogic.gdx.scenes.scene2d.actions.EventAction[T](eventClass$p.asInstanceOf[java.lang.Class[T]]) {
  var count: scala.Int = 0
  var current: scala.Int = 0
  this.count = count$p
  def handle(event: T): scala.Boolean = {
    this.current = this.current + 1
    return this.current >= this.count
  }
}