package com.badlogic.gdx.scenes.scene2d.ui

class ButtonGroup[T <: com.badlogic.gdx.scenes.scene2d.ui.Button] {
  private final val buttons: com.badlogic.gdx.utils.Array[T] = new com.badlogic.gdx.utils.Array()
  private var checkedButtons: com.badlogic.gdx.utils.Array[T] = new com.badlogic.gdx.utils.Array(1)
  private var minCheckCount: scala.Int = 0
  private var maxCheckCount: scala.Int = 1
  private var uncheckLast: scala.Boolean = true
  private var lastChecked: T = null.asInstanceOf[T]
  def this(buttons: scala.Array[T]) = {
    this()
    this.minCheckCount = 0
    this.add(buttons)
    this.minCheckCount = 1
  }
  this.minCheckCount = 1
  def add(button: T): scala.Unit = {
    if (button == null) {
      throw new java.lang.IllegalArgumentException("button cannot be null.")
    } else ()
    button.buttonGroup = null
    val shouldCheck: scala.Boolean = button.isChecked() || (this.buttons.size < this.minCheckCount)
    button.setChecked(false)
    button.buttonGroup = this
    this.buttons.add(button)
    button.setChecked(shouldCheck)
  }
  def add(buttons: scala.Array[T]): scala.Unit = {
    if (buttons == null) {
      throw new java.lang.IllegalArgumentException("buttons cannot be null.")
    } else ();
    { var i: scala.Int = 0; val n: scala.Int = buttons.length; while (i < n) { {
      this.add(buttons(i))
    }; i = i + 1 } }
  }
  def remove(button: T): scala.Unit = {
    if (button == null) {
      throw new java.lang.IllegalArgumentException("button cannot be null.")
    } else ()
    button.buttonGroup = null
    this.buttons.removeValue(button, true)
    this.checkedButtons.removeValue(button, true)
  }
  def remove(buttons: scala.Array[T]): scala.Unit = {
    if (buttons == null) {
      throw new java.lang.IllegalArgumentException("buttons cannot be null.")
    } else ();
    { var i: scala.Int = 0; val n: scala.Int = buttons.length; while (i < n) { {
      this.remove(buttons(i))
    }; i = i + 1 } }
  }
  def clear(): scala.Unit = {
    this.buttons.clear()
    this.checkedButtons.clear()
  }
  def setChecked(text: java.lang.String): scala.Unit = {
    if (text == null) {
      throw new java.lang.IllegalArgumentException("text cannot be null.")
    } else ();
    { var i: scala.Int = 0; val n: scala.Int = this.buttons.size; while (i < n) { {
      val button: T = this.buttons.get(i)
      if (button.isInstanceOf[com.badlogic.gdx.scenes.scene2d.ui.TextButton] && text.contentEquals(button.asInstanceOf[com.badlogic.gdx.scenes.scene2d.ui.TextButton].getText())) {
        button.setChecked(true)
        return
      } else ()
    }; i = i + 1 } }
  }
  def canCheck(button: T, newState: scala.Boolean): scala.Boolean = {
    if (button.isChecked$field == newState) {
      return false
    } else ()
    if (!newState) {
      if (this.checkedButtons.size <= this.minCheckCount) {
        return false
      } else ()
      this.checkedButtons.removeValue(button, true)
    } else {
      if ((this.maxCheckCount != (-1)) && (this.checkedButtons.size >= this.maxCheckCount)) {
        if (!this.uncheckLast) {
          return false
        } else ();
        { var tries: scala.Int = 0; while (true) { {
          val old: scala.Int = this.minCheckCount
          this.minCheckCount = 0
          this.lastChecked.setChecked(false)
          this.minCheckCount = old
          if (button.isChecked$field == newState) {
            return false
          } else ()
          if (this.checkedButtons.size < this.maxCheckCount) {
            /* break */ ()
          } else ()
          if ({ tries += 1; tries } > 10) {
            return false
          } else ()
        };  } }
      } else ()
      this.checkedButtons.add(button)
      this.lastChecked = button
    }
    return true
  }
  def uncheckAll(): scala.Unit = {
    val old: scala.Int = this.minCheckCount
    this.minCheckCount = 0;
    { var i: scala.Int = 0; val n: scala.Int = this.buttons.size; while (i < n) { {
      val button: T = this.buttons.get(i)
      button.setChecked(false)
    }; i = i + 1 } }
    this.minCheckCount = old
  }
  def getChecked(): T = {
    if (this.checkedButtons.size > 0) {
      return this.checkedButtons.get(0)
    } else ()
    return null.asInstanceOf[T]
  }
  def getCheckedIndex(): scala.Int = {
    if (this.checkedButtons.size > 0) {
      return this.buttons.indexOf(this.checkedButtons.get(0), true)
    } else ()
    return -1
  }
  def getAllChecked(): com.badlogic.gdx.utils.Array[T] = {
    return this.checkedButtons
  }
  def getButtons(): com.badlogic.gdx.utils.Array[T] = {
    return this.buttons
  }
  def setMinCheckCount(minCheckCount: scala.Int): scala.Unit = {
    this.minCheckCount = minCheckCount
  }
  def setMaxCheckCount(maxCheckCount$arg: scala.Int): scala.Unit = {
    var maxCheckCount: scala.Int = maxCheckCount$arg
    if (maxCheckCount == 0) {
      maxCheckCount = -1
    } else ()
    this.maxCheckCount = maxCheckCount
  }
  def setUncheckLast(uncheckLast: scala.Boolean): scala.Unit = {
    this.uncheckLast = uncheckLast
  }
}