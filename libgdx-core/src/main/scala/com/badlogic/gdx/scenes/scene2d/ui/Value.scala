package com.badlogic.gdx.scenes.scene2d.ui

abstract class Value {
  def get(): scala.Float = {
    return this.get(null)
  }
  def get(context: com.badlogic.gdx.scenes.scene2d.Actor): scala.Float
}
object Value {
  final val zero: com.badlogic.gdx.scenes.scene2d.ui.Value.Fixed = new com.badlogic.gdx.scenes.scene2d.ui.Value.Fixed(0)
  var minWidth: Value = new Value() {
    override def get(context: com.badlogic.gdx.scenes.scene2d.Actor): scala.Float = {
      if (context.isInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Layout]) {
        return context.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Layout].getMinWidth()
      } else ()
      return if (context == null) 0 else context.getWidth()
    }
  }
  var minHeight: Value = new Value() {
    override def get(context: com.badlogic.gdx.scenes.scene2d.Actor): scala.Float = {
      if (context.isInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Layout]) {
        return context.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Layout].getMinHeight()
      } else ()
      return if (context == null) 0 else context.getHeight()
    }
  }
  var prefWidth: Value = new Value() {
    override def get(context: com.badlogic.gdx.scenes.scene2d.Actor): scala.Float = {
      if (context.isInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Layout]) {
        return context.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Layout].getPrefWidth()
      } else ()
      return if (context == null) 0 else context.getWidth()
    }
  }
  var prefHeight: Value = new Value() {
    override def get(context: com.badlogic.gdx.scenes.scene2d.Actor): scala.Float = {
      if (context.isInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Layout]) {
        return context.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Layout].getPrefHeight()
      } else ()
      return if (context == null) 0 else context.getHeight()
    }
  }
  var maxWidth: Value = new Value() {
    override def get(context: com.badlogic.gdx.scenes.scene2d.Actor): scala.Float = {
      if (context.isInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Layout]) {
        return context.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Layout].getMaxWidth()
      } else ()
      return if (context == null) 0 else context.getWidth()
    }
  }
  var maxHeight: Value = new Value() {
    override def get(context: com.badlogic.gdx.scenes.scene2d.Actor): scala.Float = {
      if (context.isInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Layout]) {
        return context.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Layout].getMaxHeight()
      } else ()
      return if (context == null) 0 else context.getHeight()
    }
  }
  def percentWidth(percent: scala.Float): Value = {
    return new Value() {
      override def get(actor: com.badlogic.gdx.scenes.scene2d.Actor): scala.Float = {
        return actor.getWidth() * percent
      }
    }
  }
  def percentHeight(percent: scala.Float): Value = {
    return new Value() {
      override def get(actor: com.badlogic.gdx.scenes.scene2d.Actor): scala.Float = {
        return actor.getHeight() * percent
      }
    }
  }
  def percentWidth(percent: scala.Float, actor: com.badlogic.gdx.scenes.scene2d.Actor): Value = {
    if (actor == null) {
      throw new java.lang.IllegalArgumentException("actor cannot be null.")
    } else ()
    return new Value() {
      override def get(context: com.badlogic.gdx.scenes.scene2d.Actor): scala.Float = {
        return actor.getWidth() * percent
      }
    }
  }
  def percentHeight(percent: scala.Float, actor: com.badlogic.gdx.scenes.scene2d.Actor): Value = {
    if (actor == null) {
      throw new java.lang.IllegalArgumentException("actor cannot be null.")
    } else ()
    return new Value() {
      override def get(context: com.badlogic.gdx.scenes.scene2d.Actor): scala.Float = {
        return actor.getHeight() * percent
      }
    }
  }
  class Fixed(value$p: scala.Float) extends Value {
    private var value: scala.Float = 0.0f
    this.value = value$p
    override def get(context: com.badlogic.gdx.scenes.scene2d.Actor): scala.Float = {
      return this.value
    }
    override def toString(): java.lang.String = {
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