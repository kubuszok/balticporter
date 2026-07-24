package com.badlogic.gdx.scenes.scene2d.ui

abstract class Value {
  def get(): scala.Float = {
    return this.get(null)
  }
  def get(context: com.badlogic.gdx.scenes.scene2d.Actor): scala.Float
  class Fixed extends Value {
    private var value: scala.Float = 0.0f
    def this(value: scala.Float) = {
      this()
      this.value = value
    }
    def get(context: com.badlogic.gdx.scenes.scene2d.Actor): scala.Float = {
      return this.value
    }
    def toString(): java.lang.String = {
      return java.lang.Float.toString(this.value)
    }
  }
  object Fixed {
    final val cache: scala.Array[Fixed] = new Array[Fixed](111)
    def valueOf(value: scala.Float): Fixed = {
      if (value == 0) {
        return Value.zero
      } else ()
      if (((value >= (-10)) && (value <= 100)) && (value == value.asInstanceOf[scala.Int])) {
        var fixed: Fixed = Fixed.cache(value.asInstanceOf[scala.Int] + 10)
        if (fixed == null) {
          Fixed.cache(value.asInstanceOf[scala.Int] + 10) = {
            fixed = new Fixed(value)
            fixed
          }
        } else ()
        return fixed
      } else ()
      return new Fixed(value)
    }
  }
}
object Value {
  final val zero: Fixed = new Fixed(0)
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
}