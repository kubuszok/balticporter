package com.badlogic.gdx.scenes.scene2d.ui

abstract class Value {
  def get(): scala.Float = {
    return this.get(null)
  }
  def get(context: com.badlogic.gdx.scenes.scene2d.Actor): scala.Float
}
object Value {
  final val zero: com.badlogic.gdx.scenes.scene2d.ui.Value.Fixed = new com.badlogic.gdx.scenes.scene2d.ui.Value.Fixed(0)
  var minWidth: Value = new Value()
  var minHeight: Value = new Value()
  var prefWidth: Value = new Value()
  var prefHeight: Value = new Value()
  var maxWidth: Value = new Value()
  var maxHeight: Value = new Value()
  def percentWidth(percent: scala.Float): Value = {
    return new Value()
  }
  def percentHeight(percent: scala.Float): Value = {
    return new Value()
  }
  def percentWidth(percent: scala.Float, actor: com.badlogic.gdx.scenes.scene2d.Actor): Value = {
    if (actor == null) {
      throw new java.lang.IllegalArgumentException("actor cannot be null.")
    } else ()
    return new Value()
  }
  def percentHeight(percent: scala.Float, actor: com.badlogic.gdx.scenes.scene2d.Actor): Value = {
    if (actor == null) {
      throw new java.lang.IllegalArgumentException("actor cannot be null.")
    } else ()
    return new Value()
  }
  class Fixed(value$p: scala.Float) extends Value {
    private var value: scala.Float = 0.0f
    this.value = value$p
    def get(context: com.badlogic.gdx.scenes.scene2d.Actor): scala.Float = {
      return this.value
    }
    def toString(): java.lang.String = {
      return java.lang.Float.toString(this.value)
    }
  }
  object Fixed {
    export Value.{cache => _, valueOf => _, *}
    final val cache: scala.Array[com.badlogic.gdx.scenes.scene2d.ui.Value.Fixed] = new scala.Array[com.badlogic.gdx.scenes.scene2d.ui.Value.Fixed](111)
    def valueOf(value: scala.Float): com.badlogic.gdx.scenes.scene2d.ui.Value.Fixed = {
      if (value == 0) {
        return Value.zero
      } else ()
      if (((value >= (-10)) && (value <= 100)) && (value == value.asInstanceOf[scala.Int])) {
        var fixed: com.badlogic.gdx.scenes.scene2d.ui.Value.Fixed = com.badlogic.gdx.scenes.scene2d.ui.Value.Fixed.cache(value.asInstanceOf[scala.Int] + 10)
        if (fixed == null) {
          com.badlogic.gdx.scenes.scene2d.ui.Value.Fixed.cache(value.asInstanceOf[scala.Int] + 10) = {
            fixed = new com.badlogic.gdx.scenes.scene2d.ui.Value.Fixed(value)
            fixed
          }
        } else ()
        return fixed
      } else ()
      return new com.badlogic.gdx.scenes.scene2d.ui.Value.Fixed(value)
    }
  }
}